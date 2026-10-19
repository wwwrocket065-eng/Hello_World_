package com.glassstudy.filament

import android.content.Context
import android.util.Log
import com.google.android.filament.View
import com.google.android.filament.utils.GlassStudyModelViewer
import com.google.android.filament.utils.KTX1Loader
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Renderer parameters and studio indirect lighting. No UI or gesture ownership. */
class RenderEnvironmentController(
    private val context:Context,
    private val viewer:GlassStudyModelViewer
) {
    var configuration=RenderModuleConfig()
        private set
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
        viewer.view.ambientOcclusionOptions=viewer.view.ambientOcclusionOptions.apply{
            enabled=true
        }
        apply(configuration)
    }
    fun apply(next:RenderModuleConfig) {
        configuration=next
        viewer.camera.setExposure(next.exposureAperture,next.exposureShutter,
            next.exposureSensitivity)
        viewer.view.antiAliasing=if(next.fxaa)View.AntiAliasing.FXAA
            else View.AntiAliasing.NONE
        viewer.scene.indirectLight?.intensity=next.iblIntensity
    }
}
