package com.glassstudy.filament

import android.content.Context
import android.util.Log
import com.google.android.filament.View
import com.google.android.filament.utils.GlassStudyModelViewer
import com.google.android.filament.utils.KTX1Loader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/** Renderer parameters and studio indirect lighting. No UI or gesture ownership. */
class RenderEnvironmentController(
    private val context:Context,
    private val viewer:GlassStudyModelViewer
) {
    var configuration=RenderModuleConfig()
        private set
    private var globalBrightness=1.0

    /**
     * Global scene illumination, not just direct sunlight. 0 means BOTH
     * directional light and reflected image-based illumination are off.
     * Preserve the current brightness when patches update render settings.
     */
    fun setGlobalBrightness(value:Double) {
        require(value.isFinite() && value in 0.0..2.0)
        if(globalBrightness==value)return
        globalBrightness=value
        applyIndirectIllumination()
    }
    private fun applyIndirectIllumination() {
        val exposure=globalBrightness.pow(configuration.globalBrightnessExponent.toDouble())
        viewer.scene.indirectLight?.intensity=(configuration.iblIntensity*exposure).toFloat()
    }
    fun initialize() {
        try {
            val bytes=context.assets.open("envs/studio_ibl.ktx").use{it.readBytes()}
            val buf=ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes).flip()
            viewer.scene.indirectLight=KTX1Loader.createIndirectLight(viewer.engine,buf)
        } catch(_:java.io.FileNotFoundException) {
            // Existing fallback directional light is retained.
        } catch(e:Exception) {
            Log.w("GlassStudy","Optional IBL could not load",e)
        }
        apply(configuration)
    }
    fun apply(next:RenderModuleConfig) {
        configuration=next
        viewer.view.ambientOcclusionOptions=viewer.view.ambientOcclusionOptions.apply {
            enabled=next.ambientOcclusionEnabled
        }
        viewer.camera.setExposure(next.exposureAperture,next.exposureShutter,
            next.exposureSensitivity)
        viewer.view.antiAliasing=if(next.fxaa)View.AntiAliasing.FXAA
            else View.AntiAliasing.NONE
        applyIndirectIllumination()
    }
}
