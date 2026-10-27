package com.glassstudy.filament

import android.app.Activity
import android.content.SharedPreferences
import android.util.Log
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import com.google.android.filament.Skybox
import com.google.android.filament.utils.GlassStudyModelViewer

/**
 * Owns ONLY the physical sky backdrop and floor geometry.
 * The visible sky is derived from the current sun, never from a studio HDRI,
 * artificial white fill, arbitrary old "sky RGB", or a special zero-only swap.
 * No sky/environment reflection lighting is generated here; that is the
 * RenderEnvironmentController's job.
 */
class SceneEnvironmentController(
    private val activity:Activity,
    private val viewer:GlassStudyModelViewer,
    private val preferences:SharedPreferences,
    private val onLightingModeChanged:(LightEnvironmentMode)->Unit
):SceneSettingsPanel.State {
    override var module:SceneModuleConfig=SceneModuleConfig()
        private set
    override var floorMode:String=preferences.getString("floorMode","checker")
        ?.takeIf { it in setOf("checker","white","none") } ?: "checker"
        private set
    override var lightingMode:LightEnvironmentMode=LightEnvironmentMode.from(
        preferences.getString("physicalLightingMode","atmosphere") ?: "atmosphere")
        private set

    private var activeSkybox:Skybox?=null
    private var globalBrightness=1.0
    private var currentElevation=.9
    private var currentAzimuth=1.3
    private var atmosphereHaze=.3
    private val lightHandler=Handler(Looper.getMainLooper())
    private val tracingExecutor=Executors.newSingleThreadExecutor()
    private var pendingTrace:Runnable?=null
    private var traceRevision=0L
    private var stopped=false
    private var gridMode=false
    private var glassProfile:ByteArray?=runCatching {
        activity.assets.open(ParametricGoblet.PATH).use { it.readBytes() }
    }.getOrNull()
    private var lastCachedTrace:String="не рассчитана"
    val causticDiagnostics:String get()=lastCachedTrace

    /** A matching model profile is required; wireframe has no glass caustics. */
    fun setGlassProfile(profile:ByteArray?,wireframe:Boolean) {
        val same=profile?.contentEquals(glassProfile ?: byteArrayOf())==true
        if(same && gridMode==wireframe)return
        glassProfile=profile?.clone()
        gridMode=wireframe
        updateGlassLight(force=true)
    }

    /** Debounced CPU photon trace. Only the result touches Filament's UI thread. */
    private fun updateGlassLight(force:Boolean=false) {
        if(stopped)return
        pendingTrace?.let(lightHandler::removeCallbacks)
        pendingTrace=null
        val token=++traceRevision
        val profile=glassProfile?.clone()
        if(floorMode=="none" || gridMode || profile==null ||
            globalBrightness<.015 || kotlin.math.sin(currentElevation)<.27) {
            viewer.setCausticGlb(null)
            lastCachedTrace=if(gridMode)"выключена: сетка" else "выключена: нет освещения/пола"
            return
        }
        val az=currentAzimuth
        val el=currentElevation
        val intensity=globalBrightness
        val work=Runnable {
            tracingExecutor.execute {
                try {
                    val traced=GlassRayTrace.trace(profile,az,el,intensity)
                    val overlay=if(traced!=null)GlassCausticsGlb.create(traced,intensity) else null
                    lightHandler.post {
                        if(stopped || token!=traceRevision)return@post
                        try {
                            viewer.setCausticGlb(overlay)
                            lastCachedTrace=if(traced==null)"солнце ниже горизонта" else
                                "CPU Snell: ${traced.size}², попаданий ${traced.hits}, " +
                                "преломлённых ${traced.refracted}, кеш"
                        } catch(e:Exception) {
                            Log.e("GlassStudy","Failed to render cached photon map",e)
                            lastCachedTrace="ошибка отображения: ${e.javaClass.simpleName}"
                        }
                    }
                } catch(e:Exception) {
                    Log.e("GlassStudy","Glass photon projection failed",e)
                    lightHandler.post {
                        if(!stopped && token==traceRevision) {
                            viewer.setCausticGlb(null)
                            lastCachedTrace="ошибка трассировки: ${e.javaClass.simpleName}"
                        }
                    }
                }
            }
        }
        pendingTrace=work
        lightHandler.postDelayed(work,if(force)80L else 320L)
    }

    fun setLightState(brightness:Double,elevation:Double,azimuth:Double) {
        require(brightness.isFinite() && brightness in 0.0..2.0)
        require(elevation.isFinite() && elevation in 0.0..Math.PI)
        require(azimuth.isFinite())
        if(brightness==globalBrightness && elevation==currentElevation &&
            azimuth==currentAzimuth)return
        globalBrightness=brightness
        currentElevation=elevation
        currentAzimuth=azimuth
        updatePhysicalSky()
        updateGlassLight()
    }

    fun configureAtmosphereHaze(value:Double) {
        require(value.isFinite() && value in 0.0..1.0)
        atmosphereHaze=value
        updatePhysicalSky()
    }

    override fun chooseLightingMode(mode:LightEnvironmentMode) {
        if(mode==lightingMode)return
        lightingMode=mode
        preferences.edit().putString("physicalLightingMode",mode.key).apply()
        updatePhysicalSky()
        onLightingModeChanged(mode)
    }

    /** Legacy scene signed settings remain parsable; physical modes ignore them. */
    fun applyConfiguration(next:SceneModuleConfig) {
        if(module==next)return
        module=next
        applyFloor()
        if(!preferences.contains("physicalLightingMode")) {
            val preferred=LightEnvironmentMode.from(next.defaultLightingMode)
            if(preferred!=lightingMode) {
                lightingMode=preferred
                onLightingModeChanged(lightingMode)
            }
        }
        updatePhysicalSky()
    }

    override fun chooseFloor(mode:String) {
        require(mode in module.floorOrder) { "Unknown floor mode" }
        floorMode=mode
        preferences.edit().putString("floorMode",mode).apply()
        applyFloor()
    }

    /** A single allocated native Skybox with a physically derived background. */
    fun applySkybox() {
        if(activeSkybox==null) {
            activeSkybox=Skybox.Builder().color(0f,0f,0f,1f).build(viewer.engine)
            viewer.scene.skybox=activeSkybox
        }
        updatePhysicalSky()
    }

    private fun updatePhysicalSky() {
        val rgb=PhysicalAtmosphereModel.backgroundRgb(lightingMode,
            currentElevation,atmosphereHaze,globalBrightness)
        activeSkybox?.setColor(rgb[0],rgb[1],rgb[2],1f)
    }

    fun applyFloor() {
        try {
            viewer.setFloorGlb(when(floorMode) {
                "checker" -> SceneFloorGlb.create(true,module.floorWidth,module.tileWidth,
                    module.darkLevel,module.lightLevel)
                "white" -> SceneFloorGlb.create(false,module.floorWidth,module.tileWidth,
                    module.darkLevel,module.whiteLevel)
                else -> null
            })
            updateGlassLight(force=true)
        } catch(e:Exception) {
            Log.e("GlassStudy","Floor GLB rejected; keeping main model running",e)
            floorMode="none"
            preferences.edit().putString("floorMode","none").apply()
            try { viewer.setFloorGlb(null) } catch(_:Exception) {}
            try { updateGlassLight(force=true) } catch(_:Exception) {}
        }
    }

    fun release() {
        stopped=true
        traceRevision++
        pendingTrace?.let(lightHandler::removeCallbacks)
        lightHandler.removeCallbacksAndMessages(null)
        tracingExecutor.shutdownNow()
        try { viewer.setCausticGlb(null) } catch(_:Exception) {}
        activeSkybox?.let {
            if(viewer.scene.skybox===it)viewer.scene.skybox=null
            viewer.engine.destroySkybox(it)
        }
        activeSkybox=null
    }
}
