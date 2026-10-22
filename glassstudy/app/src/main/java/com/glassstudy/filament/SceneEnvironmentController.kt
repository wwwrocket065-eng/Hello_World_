package com.glassstudy.filament

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import com.google.android.filament.Skybox
import com.google.android.filament.utils.GlassStudyModelViewer
import com.google.android.filament.utils.KTX1Loader
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Single owner of skybox, floor and user choices. The panel never accesses Filament. */
class SceneEnvironmentController(
    private val activity:Activity,
    private val viewer:GlassStudyModelViewer,
    private val preferences:SharedPreferences,
    private val dimTexturedBackground:(Float)->Unit
):SceneSettingsPanel.State {

    override var module:SceneModuleConfig=SceneModuleConfig()
        private set
    override var floorMode:String=preferences.getString("floorMode","checker")
        ?.takeIf { it in setOf("checker","white","none") } ?: "checker"
        private set
    override var skyMode:String=preferences.getString("skyMode","gray")
        ?.takeIf { it in setOf("gray","studio","white","dark","custom") } ?: "gray"
        private set
    override var customSkyColor:Int=preferences.getInt("customSkyColor",Color.rgb(110,110,110))
        private set

    private var activeSkybox:Skybox?=null
    private var globalBrightness=1.0
    private var solidBackgroundRgb:Int=Color.rgb(110,110,110)
    private var texturedBackground=false

    /**
     * Continuous background dimming for EVERY brightness change, including
     * 1%→0% with no exact-zero Skybox swap. Flat skyboxes use native
     * Skybox.setColor() without allocating any Filament resources.
     *
     * Filament ignores setColor() for texture-backed skyboxes, so ONLY the
     * studio texture uses a lightweight black screen overlay behind Android UI.
     * Direct and indirect lighting are always scaled physically separately.
     */
    fun setGlobalBrightness(value:Double) {
        require(value.isFinite() && value in 0.0..2.0)
        if(globalBrightness==value)return
        globalBrightness=value
        updateBackgroundBrightness()
    }

    private fun updateBackgroundBrightness() {
        val multiplier=LightingResponseCurve.sky(globalBrightness,
            module.skyBrightnessExponent,module.skyBrightnessCeiling).toFloat()
        if(texturedBackground) {
            // 0%: fully black; 1%: nearly black, not a hard 0/1 switch.
            // Studio image is untouched when 100% or brighter.
            dimTexturedBackground(1f-multiplier.coerceAtMost(1f))
        } else {
            dimTexturedBackground(0f)
            val rgb=solidBackgroundRgb
            activeSkybox?.setColor(
                (Color.red(rgb)/255f*multiplier).coerceIn(0f,1f),
                (Color.green(rgb)/255f*multiplier).coerceIn(0f,1f),
                (Color.blue(rgb)/255f*multiplier).coerceIn(0f,1f),1f)
        }
    }

    /** Validated config snapshot; geometry is refreshed only when effective values change. */
    fun applyConfiguration(next:SceneModuleConfig) {
        if(module==next)return
        module=next
        applyFloor()
        // Reapply only after a skybox has been created; preserve the user's skyMode.
        if(activeSkybox!=null)applySkybox()
    }

    override fun chooseSky(mode:String) {
        require(mode in module.skyboxOrder && mode!="custom") { "Unknown skybox mode" }
        skyMode=mode
        persist()
        applySkybox()
    }
    override fun chooseFloor(mode:String) {
        require(mode in module.floorOrder) { "Unknown floor mode" }
        floorMode=mode
        persist()
        applyFloor()
    }
    override fun chooseCustomSky(color:Int) {
        customSkyColor=color or -0x1000000
        skyMode="custom"
        persist()
        applySkybox()
    }

    private fun persist() {
        preferences.edit().putString("floorMode",floorMode)
            .putString("skyMode",skyMode).putInt("customSkyColor",customSkyColor).apply()
    }

    /** The exact previous IBL and skybox handling stays in the render owner. */
    fun applySkybox() {
        val rgb=when(skyMode) {
            "white" -> Color.rgb(module.whiteSkyRed,module.whiteSkyGreen,module.whiteSkyBlue)
            "dark" -> Color.rgb(module.darkSkyRed,module.darkSkyGreen,module.darkSkyBlue)
            "custom" -> customSkyColor
            else -> Color.rgb(module.graySkyRed,module.graySkyGreen,module.graySkyBlue)
        }
        var nextIsTextured=false
        val next=if(skyMode=="studio") {
            try {
                val raw=activity.assets.open("envs/studio_ibl.ktx").use{it.readBytes()}
                val data=ByteBuffer.allocateDirect(raw.size).order(ByteOrder.nativeOrder())
                data.put(raw).flip()
                KTX1Loader.createSkybox(viewer.engine,data).also {
                    nextIsTextured=true
                }
            } catch(e:Exception) {
                Log.w("GlassStudy","Studio Skybox unavailable; using gray background",e)
                Skybox.Builder().color(module.graySkyRed/255f,module.graySkyGreen/255f,
                    module.graySkyBlue/255f,1f).build(viewer.engine)
            }
        } else Skybox.Builder()
            .color(Color.red(rgb)/255f,Color.green(rgb)/255f,Color.blue(rgb)/255f,1f)
            .build(viewer.engine)
        // Keep the selected Skybox object stable while changing its brightness.
        // Unlike the old exact-zero swap, there is NO jump when crossing 1→0%.
        viewer.scene.skybox=next
        activeSkybox?.let{viewer.engine.destroySkybox(it)}
        activeSkybox=next
        solidBackgroundRgb=rgb
        texturedBackground=nextIsTextured
        updateBackgroundBrightness()
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
            Log.e("GlassStudy","Floor GLB rejected; keeping the main model running",e)
            floorMode="none"
            persist()
            try{viewer.setFloorGlb(null)}catch(_:Exception){}
        }
    }

    fun release() {
        // Leave Filament resources in a consistent state on Activity destruction.
        activeSkybox?.let{
            if(viewer.scene.skybox===it)viewer.scene.skybox=null
            viewer.engine.destroySkybox(it)
        }
        activeSkybox=null
        dimTexturedBackground(0f)
        texturedBackground=false
    }
}
