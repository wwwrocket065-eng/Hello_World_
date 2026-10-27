package com.glassstudy.filament

import android.content.Context
import android.util.Log
import com.google.android.filament.IndirectLight
import com.google.android.filament.Texture
import com.google.android.filament.View
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import com.google.android.filament.utils.GlassStudyModelViewer
import kotlin.math.abs

/**
 * Owner of physically interpretable outdoor / space lighting.
 *
 * VACUUM: absolutely no environment lighting (Scene.indirectLight=null).
 * ATMOSPHERE: a single analytic Rayleigh/Mie-inspired sky is used for both
 * diffuse spherical harmonics and a physically directional specular cubemap.
 * Filament prefilters the cubemap for the existing PBR glass material.
 * VACUUM has neither diffuse nor specular environment. No studio HDRI/fill.
 * No screen-space refraction, multibounce light or caustics are claimed.
 */
class RenderEnvironmentController(
    private val context:Context,
    private val viewer:GlassStudyModelViewer
) {
    var configuration=RenderModuleConfig()
        private set
    private var fxaaOverride:Boolean?=null
    private var wireframeMode=false
    fun setWireframeMode(enabled:Boolean) {
        if(wireframeMode==enabled)return
        wireframeMode=enabled
        applyAntiAliasing()
    }
    val fxaaEnabled:Boolean get()=fxaaOverride ?: configuration.fxaa

    /** User UI override survives applying later signed render patches. */
    fun setFxaaOverride(value:Boolean?) {
        fxaaOverride=value
        applyAntiAliasing()
    }

    private fun applyAntiAliasing() {
        // FXAA alone has very little visible effect on transparent glass.
        // Use 4x MSAA for the shaded render as well; neither option changes
        // normals, mesh density or specular response.
        val shadedAA=fxaaEnabled && !wireframeMode
        viewer.view.antiAliasing=if(shadedAA)View.AntiAliasing.FXAA
            else View.AntiAliasing.NONE
        viewer.view.multiSampleAntiAliasingOptions=
            View.MultiSampleAntiAliasingOptions().apply {
                enabled=shadedAA
                sampleCount=4
            }
    }
    private var globalBrightness=1.0
    private var sunElevation=.9
    private var sunAzimuth=1.3
    private var noonSunLux=100000.0
    var lightingMode=LightEnvironmentMode.ATMOSPHERE
        private set

    private var skyLight:IndirectLight?=null
    private var reflectionMap:Texture?=null
    private var reflectionsDisabledForSession=false
    private var lastReflectionError:String?=null

    /** Actual runtime IBL allocation and failure state, not configured intentions. */
    fun reflectionDiagnostics():String = when {
        lightingMode==LightEnvironmentMode.VACUUM -> "отключён (вакуум)"
        !configuration.reflectionsEnabled -> "карта отключена настройками; рассеяние SH=${skyLight!=null}"
        reflectionsDisabledForSession ->
            "создание карты не удалось (${lastReflectionError ?: "ошибка GPU"}); SH=${skyLight!=null}"
        reflectionMap!=null && skyLight!=null ->
            "HDR cubemap ${configuration.reflectionMapSize} px, " +
                "prefilter ${configuration.reflectionSamples} samples, SH: включён"
        else -> "карта отсутствует; SH=${skyLight!=null} (проверить GPU/журнал)"
    }
    private var previousShElevation=Double.NaN
    private var previousShAzimuth=Double.NaN
    private var previousReflectionElevation=Double.NaN
    private var previousReflectionAzimuth=Double.NaN

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

    /**
     * Create Filament's native prefiltered reflection cubemap. The same sky
     * radiance function and LightDirectionMath sun vector drive both specular
     * reflections and diffuse SH; no extra lighting pipeline or static HDRI.
     * Rebuilt only on signed angular thresholds, NOT per frame/touch sample.
     */
    private fun createSkyReflectionMap():Texture {
        val engine=viewer.engine
        val size=configuration.reflectionMapSize
        check(Texture.isTextureFormatSupported(engine,Texture.InternalFormat.RGB16F)) {
            "RGB16F cubemap not supported by graphics driver"
        }
        val bytesPerFace=size*size*3*Float.SIZE_BYTES
        val rgb=ByteBuffer.allocateDirect(bytesPerFace*6).order(ByteOrder.nativeOrder())
        val sun=LightDirectionMath.source(sunAzimuth,sunElevation)
        val haze=configuration.atmosphereHaze.toDouble()
        for(face in 0 until 6)for(row in 0 until size)for(col in 0 until size) {
            val u=2.0*(col+0.5)/size-1.0
            val v=2.0*(row+0.5)/size-1.0
            // Conventional cubemap faces: +X,-X,+Y,-Y,+Z,-Z.
            val vector=when(face) {
                0 -> doubleArrayOf(1.0,-v,-u)
                1 -> doubleArrayOf(-1.0,-v,u)
                2 -> doubleArrayOf(u,1.0,v)
                3 -> doubleArrayOf(u,-1.0,-v)
                4 -> doubleArrayOf(u,-v,1.0)
                else -> doubleArrayOf(-u,-v,-1.0)
            }
            val length=sqrt(vector[0]*vector[0]+vector[1]*vector[1]+vector[2]*vector[2])
            val dx=vector[0]/length;val dy=vector[1]/length;val dz=vector[2]/length
            // Lower-hemisphere bounce is missing from the analytic sky: a lit
            // diffuse floor is not black when reflected by the underside.
            // This is an inexpensive approximate ground radiance, not HDRI.
            val colors=if(dy<0.0)doubleArrayOf(.22,.215,.20)
                else PhysicalAtmosphereModel.skyRadiance(dx,dy,dz,
                    sun.x.toDouble(),sun.y.toDouble(),sun.z.toDouble(),haze)
            val cosine=(dx*sun.x.toDouble()+dy*sun.y.toDouble()+dz*sun.z.toDouble()).coerceIn(-1.0,1.0)
            // Sub-texel solar disc approximated by an energy-limited angular
            // lobe to preserve a visible highlight after cubemap prefilter.
            val sunGlint=if(dy>0.0)6.0*kotlin.math.exp((cosine-1.0)/.0016)
                else 0.0
            val tint=PhysicalAtmosphereModel.sunlightRgb(
                lightingMode,sunElevation,configuration.atmosphereExtinction.toDouble())
            for(i in 0..2) rgb.putFloat((colors[i]+sunGlint*tint[i].toDouble()).toFloat())
        }
        rgb.flip()
        val cubemap=Texture.Builder().width(size).height(size)
            .levels(Integer.numberOfTrailingZeros(size)+1)
            .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
            .format(Texture.InternalFormat.RGB16F).build(engine)
        try {
            val offsets=IntArray(6) { it*bytesPerFace }
            val options=Texture.PrefilterOptions().apply {
                sampleCount=configuration.reflectionSamples
                mirror=true
            }
            cubemap.generatePrefilterMipmap(engine,
                Texture.PixelBufferDescriptor(rgb,Texture.Format.RGB,Texture.Type.FLOAT),
                offsets,options)
            return cubemap
        } catch(error:Exception) {
            engine.destroyTexture(cubemap)
            throw error
        }
    }

    private fun angleDelta(a:Double,b:Double):Double {
        if(!b.isFinite())return Double.POSITIVE_INFINITY
        return abs(((a-b+Math.PI)%(2*Math.PI)+2*Math.PI)%(2*Math.PI)-Math.PI)
    }

    private fun updateAtmosphere() {
        updateExposure()
        if(lightingMode==LightEnvironmentMode.VACUUM) {
            viewer.scene.indirectLight=null
            skyLight?.let { viewer.engine.destroyIndirectLight(it) }
            skyLight=null
            reflectionMap?.let { viewer.engine.destroyTexture(it) }
            reflectionMap=null
            previousReflectionElevation=Double.NaN
            previousReflectionAzimuth=Double.NaN
            return
        }
        val shThreshold=Math.toRadians(configuration.atmosphereShUpdateDegrees.toDouble())
        val reflectionThreshold=Math.toRadians(configuration.reflectionUpdateDegrees.toDouble())
        val needReflection=configuration.reflectionsEnabled && !reflectionsDisabledForSession &&
            globalBrightness>0.0 &&
            (reflectionMap==null ||
                abs(sunElevation-previousReflectionElevation)>reflectionThreshold ||
                angleDelta(sunAzimuth,previousReflectionAzimuth)>reflectionThreshold)
        val rebuild=skyLight==null ||
            abs(sunElevation-previousShElevation)>shThreshold ||
            angleDelta(sunAzimuth,previousShAzimuth)>shThreshold*2.0 ||
            needReflection || (!configuration.reflectionsEnabled && reflectionMap!=null)
        if(rebuild) {
            var replacementMap:Texture?=null
            if(needReflection) {
                try {
                    replacementMap=createSkyReflectionMap()
                } catch(error:Exception) {
                    // Keep existing map, or render sky SH alone if the GPU rejects RGB16F.
                    Log.w("GlassStudy","Filament reflection map unavailable; keeping diffuse sky",error)
                    reflectionsDisabledForSession=true
                    lastReflectionError=error.javaClass.simpleName
                }
            }
            try {
                val sh=PhysicalAtmosphereModel.skyRadianceSh(
                    sunElevation,sunAzimuth,configuration.atmosphereHaze.toDouble())
                val cubemap=if(configuration.reflectionsEnabled)
                    replacementMap ?: reflectionMap else null
                val builder=IndirectLight.Builder().radiance(3,sh)
                if(cubemap!=null)builder.reflections(cubemap)
                val next=builder.intensity(0f).build(viewer.engine)
                viewer.scene.indirectLight=next
                skyLight?.let { viewer.engine.destroyIndirectLight(it) }
                skyLight=next
                if(replacementMap!=null) {
                    reflectionMap?.let { viewer.engine.destroyTexture(it) }
                    reflectionMap=replacementMap
                    lastReflectionError=null
                    replacementMap=null
                    previousReflectionElevation=sunElevation
                    previousReflectionAzimuth=sunAzimuth
                } else if(!configuration.reflectionsEnabled) {
                    reflectionMap?.let { viewer.engine.destroyTexture(it) }
                    reflectionMap=null
                }
                previousShElevation=sunElevation
                previousShAzimuth=sunAzimuth
            } catch(error:Exception) {
                Log.e("GlassStudy","Sky IBL update failed; preserving last working light",error)
                replacementMap?.let { viewer.engine.destroyTexture(it) }
            }
        }
        val illuminance=PhysicalAtmosphereModel.skyIlluminance(
            lightingMode,sunElevation,
            configuration.atmosphereSkyIlluminance.toDouble(),
            configuration.atmosphereHorizonFraction.toDouble())
        val brightness=LightingResponseCurve.intensity(globalBrightness,
            configuration.globalBrightnessExponent.toDouble())
        // One intensity scales BOTH the diffuse sky and the specular cubemap.
        // At exactly 0% there is neither indirect diffuse nor reflected light.
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
        val newReflections=newAtmosphere ||
            next.reflectionsEnabled!=configuration.reflectionsEnabled ||
            next.reflectionMapSize!=configuration.reflectionMapSize ||
            next.reflectionSamples!=configuration.reflectionSamples ||
            next.reflectionUpdateDegrees!=configuration.reflectionUpdateDegrees
        configuration=next
        if(newAtmosphere || newReflections) {
            previousShElevation=Double.NaN
            previousShAzimuth=Double.NaN
        }
        if(newReflections) {
            previousReflectionElevation=Double.NaN
            previousReflectionAzimuth=Double.NaN
            reflectionsDisabledForSession=false
            lastReflectionError=null
        }
        viewer.view.ambientOcclusionOptions=viewer.view.ambientOcclusionOptions.apply {
            enabled=next.ambientOcclusionEnabled
        }
        // updateAtmosphere() applies the current physical auto-exposure.
        applyAntiAliasing()
        updateAtmosphere()
    }

    fun release() {
        viewer.scene.indirectLight=null
        skyLight?.let { viewer.engine.destroyIndirectLight(it) }
        skyLight=null
        reflectionMap?.let { viewer.engine.destroyTexture(it) }
        reflectionMap=null
    }
}
