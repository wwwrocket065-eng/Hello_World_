package com.glassstudy.filament

import android.app.Activity
import android.content.SharedPreferences
import android.util.Log
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
    // Real glass remains under Filament's glTF/PBR renderer.
    // Reject unlit photon overlays that obscured the checker / white floor.
    val causticDiagnostics:String get()="отключена: без искусственной накладки"

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
        } catch(e:Exception) {
            Log.e("GlassStudy","Floor GLB rejected; keeping main model running",e)
            floorMode="none"
            preferences.edit().putString("floorMode","none").apply()
            try { viewer.setFloorGlb(null) } catch(_:Exception) {}
        }
    }

    fun release() {
        try { viewer.setCausticGlb(null) } catch(_:Exception) {}
        activeSkybox?.let {
            if(viewer.scene.skybox===it)viewer.scene.skybox=null
            viewer.engine.destroySkybox(it)
        }
        activeSkybox=null
    }
}
