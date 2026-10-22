package com.glassstudy.filament

import android.content.Context
import android.util.Log
import com.google.android.filament.IndirectLight
import com.google.android.filament.View
import com.google.android.filament.utils.GlassStudyModelViewer
import kotlin.math.abs

/**
 * Owner of physically interpretable outdoor / space lighting.
 *
 * VACUUM: absolutely no environment lighting (Scene.indirectLight=null).
 * ATMOSPHERE: analytic Rayleigh+Mie-inspired clear sky projected into
 * 3-band SH via Filament IndirectLight.Builder.radiance(3, ...).
 * NO studio_ibl.ktx and NO constant fabricated ambient term.
 *
 * Caveat: SH models diffuse sky irradiance, NOT full prefiltered specular
 * sky reflection nor path-traced caustics; those need a dedicated renderer.
 */
class RenderEnvironmentController(
    private val context:Context,
    private val viewer:GlassStudyModelViewer
) {
    var configuration=RenderModuleConfig()
        private set
    private var globalBrightness=1.0
    private var sunElevation=.9
    private var sunAzimuth=1.3
    private var noonSunLux=100000.0
    var lightingMode=LightEnvironmentMode.ATMOSPHERE
        private set

    private var skyLight:IndirectLight?=null
    private var previousShElevation=Double.NaN
    private var previousShAzimuth=Double.NaN

    /** Cached mode controls both physical light model and actual IBL. */
    fun setLightingEnvironment(mode:LightEnvironmentMode,brightness:Double,
                               elevation:Double,azimuth:Double) {
        require(brightness.isFinite() && brightness in 0.0..2.0)
        require(elevation.isFinite() && elevation in 0.0..Math.PI)
        require(azimuth.isFinite())
        val changedMode=lightingMode!=mode
        lightingMode=mode
        globalBrightness=brightness
        sunElevation=elevation
        sunAzimuth=azimuth
        if(changedMode) {
            previousShElevation=Double.NaN
            previousShAzimuth=Double.NaN
        }
        updateAtmosphere()
    }

    /** Reads the same signed camera-module solar power as the direct light. */
    fun configureNoonSunLux(lux:Double) {
        require(lux.isFinite() && lux in 100.0..1000000.0)
        noonSunLux=lux
        updateExposure()
    }

    private fun updateExposure() {
        val iso=PhysicalExposureModel.sensitivity(
            lightingMode,sunElevation,noonSunLux,
            configuration.atmosphereExtinction.toDouble(),
            configuration.atmosphereSkyIlluminance.toDouble(),
            configuration.atmosphereHorizonFraction.toDouble(),
            configuration.exposureSensitivity.toDouble(),
            configuration.atmosphereExposureReferenceLux.toDouble(),
            configuration.atmosphereExposureMaxGain.toDouble())
        viewer.camera.setExposure(configuration.exposureAperture,
            configuration.exposureShutter,iso)
    }

    fun directTransmission(elevation:Double):Double=
        PhysicalAtmosphereModel.solarTransmission(lightingMode,elevation,
            configuration.atmosphereExtinction.toDouble())

    fun directColor(elevation:Double):FloatArray=
        PhysicalAtmosphereModel.sunlightRgb(lightingMode,elevation,
            configuration.atmosphereExtinction.toDouble())

    private fun updateAtmosphere() {
        // Change camera sensitivity only; do not inject any fake light.
        updateExposure()
        if(lightingMode==LightEnvironmentMode.VACUUM) {
            if(skyLight!=null) {
                viewer.scene.indirectLight=null
                skyLight?.let { viewer.engine.destroyIndirectLight(it) }
                skyLight=null
            }
            return
        }
        // Changing direct brightness only needs one lightweight setIntensity.
        // Sky direction is discretized a few degrees to avoid allocating an
        // IndirectLight + 3-band coefficients on every 60/120fps touch event.
        val threshold=Math.toRadians(configuration.atmosphereShUpdateDegrees.toDouble())
        val angularDelta=if(previousShAzimuth.isFinite())
            abs(((sunAzimuth-previousShAzimuth+Math.PI)%(2*Math.PI)+
                2*Math.PI)%(2*Math.PI)-Math.PI) else Double.POSITIVE_INFINITY
        if(skyLight==null || abs(sunElevation-previousShElevation)>threshold ||
            angularDelta>threshold*2.0) {
            try {
                val sh=PhysicalAtmosphereModel.skyRadianceSh(
                    sunElevation,sunAzimuth,configuration.atmosphereHaze.toDouble())
                val next=IndirectLight.Builder().radiance(3,sh)
                    .intensity(0f).build(viewer.engine)
                viewer.scene.indirectLight=next
                skyLight?.let { viewer.engine.destroyIndirectLight(it) }
                skyLight=next
                previousShElevation=sunElevation
                previousShAzimuth=sunAzimuth
            } catch(e:Exception) {
                Log.e("GlassStudy","Analytic atmosphere SH rejected",e)
                viewer.scene.indirectLight=null
                skyLight?.let { viewer.engine.destroyIndirectLight(it) }
                skyLight=null
            }
        }
        val illuminance=PhysicalAtmosphereModel.skyIlluminance(
            lightingMode,sunElevation,
            configuration.atmosphereSkyIlluminance.toDouble(),
            configuration.atmosphereHorizonFraction.toDouble())
        val brightness=LightingResponseCurve.intensity(globalBrightness,
            configuration.globalBrightnessExponent.toDouble())
        skyLight?.intensity=(illuminance*brightness).toFloat()
    }

    fun initialize() {
        // NEVER import old studio_ibl.ktx. The SH environment is calculated
        // analytically from the sun, and absent in vacuum mode.
        apply(configuration)
    }

    fun apply(next:RenderModuleConfig) {
        val newAtmosphere=next.atmosphereHaze!=configuration.atmosphereHaze ||
            next.atmosphereShUpdateDegrees!=configuration.atmosphereShUpdateDegrees
        configuration=next
        if(newAtmosphere) {
            previousShElevation=Double.NaN
            previousShAzimuth=Double.NaN
        }
        viewer.view.ambientOcclusionOptions=viewer.view.ambientOcclusionOptions.apply {
            enabled=next.ambientOcclusionEnabled
        }
        // updateAtmosphere() applies the current physical auto-exposure.
        viewer.view.antiAliasing=if(next.fxaa)View.AntiAliasing.FXAA
            else View.AntiAliasing.NONE
        updateAtmosphere()
    }

    fun release() {
        viewer.scene.indirectLight=null
        skyLight?.let { viewer.engine.destroyIndirectLight(it) }
        skyLight=null
    }
}
