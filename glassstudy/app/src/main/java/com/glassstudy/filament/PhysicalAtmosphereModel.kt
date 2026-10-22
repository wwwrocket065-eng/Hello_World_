package com.glassstudy.filament

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Real physical environment selection; there is deliberately NO studio mode. */
enum class LightEnvironmentMode(val key:String) {
    VACUUM("vacuum"),
    ATMOSPHERE("atmosphere");
    companion object {
        fun from(key:String):LightEnvironmentMode =
            entries.firstOrNull { it.key==key } ?: ATMOSPHERE
    }
}

/**
 * Compact clear-sky single-scattering approximation in linear color space.
 *
 * This is not a full multi-wavelength volumetric atmosphere solver; unlike a
 * fixed studio IBL, EVERY result depends on solar altitude and direction.
 * All atmosphere coefficients come from signed RenderModuleConfig fields.
 * Vacuum must contain NO diffuse skylight and NO atmospheric extinction.
 */
object PhysicalAtmosphereModel {
    fun airMass(sunElevationRad:Double):Double {
        val altitude=Math.toDegrees(
            acos(cos(sunElevationRad).coerceIn(-1.0,1.0))).coerceIn(0.0,180.0)
        val deg=if(altitude<=90.0)altitude else 180.0-altitude
        // Kasten-Young optical air mass approximation, valid from horizon up.
        return 1.0/(sin(Math.toRadians(deg)).coerceAtLeast(0.0)+
            0.50572*(deg+6.07995).pow(-1.6364))
    }

    fun solarTransmission(mode:LightEnvironmentMode,elevation:Double,
                          extinction:Double):Double {
        if(mode==LightEnvironmentMode.VACUUM)return 1.0
        require(extinction in 0.001..0.60)
        return exp(-extinction*airMass(elevation))
    }

    fun skyIlluminance(mode:LightEnvironmentMode,elevation:Double,
                      zenithLux:Double,horizonFraction:Double):Double {
        if(mode==LightEnvironmentMode.VACUUM)return 0.0
        require(zenithLux in 0.0..80000.0 && horizonFraction in 0.0..0.3)
        val height=sin(elevation).coerceIn(0.0,1.0)
        return zenithLux*(horizonFraction+(1.0-horizonFraction)*sqrt(height))
    }

    /**
     * Approximate Rayleigh blue and lower-elevation warm aerosol haze,
     * computed for any sky sample direction and the current solar direction.
     * This is diffuse skylight. The solar disk itself is provided separately
     * by Filament's directional SUN light, not double-counted into SH.
     */
    fun skyRadiance(directionX:Double,directionY:Double,directionZ:Double,
                    sunX:Double,sunY:Double,sunZ:Double,haze:Double):DoubleArray {
        require(haze in 0.0..1.0)
        if(directionY<0.0)return doubleArrayOf(0.0,0.0,0.0)
        val horizon=1.0-directionY.coerceIn(0.0,1.0)
        val towardSun=(directionX*sunX+directionY*sunY+directionZ*sunZ)
            .coerceIn(-1.0,1.0)
        val rayleigh=0.75*(1.0+towardSun*towardSun)
        val mie=(1.0-haze*0.55)/(1.0+0.65*0.65-1.3*towardSun)
            .coerceAtLeast(.065).pow(1.5)
        val sunHeight=sunY.coerceIn(0.0,1.0)
        val lowSun=exp(-sunHeight*10.0)
        val angular=0.6+0.4*rayleigh
        val air=(0.65+0.35*horizon)
        val blueBase=doubleArrayOf(.36,.62,1.0)
        val aerosolBase=doubleArrayOf(1.0,.76,.46)
        val aerosol=0.025+0.08*haze*mie.coerceAtMost(8.0)*(0.3+0.7*horizon)
        return DoubleArray(3) { idx ->
            (blueBase[idx]*angular*air*(1.0-.55*lowSun)+
                aerosol*aerosolBase[idx]*(1.0+2.5*lowSun))
                .coerceAtLeast(0.0)
        }
    }

    /** Separate background color approximation, derived from the same sky. */
    fun backgroundRgb(mode:LightEnvironmentMode,elevation:Double,
                      haze:Double,brightness:Double):FloatArray {
        if(mode==LightEnvironmentMode.VACUUM || brightness==0.0)
            return floatArrayOf(0f,0f,0f)
        val sun=LightDirectionMath.source(0.0,elevation)
        val colors=skyRadiance(0.0,0.8,0.6,sun.x.toDouble(),sun.y.toDouble(),
            sun.z.toDouble(),haze)
        val level=LightingResponseCurve.sky(brightness,1.0,1.25)*.45
        return FloatArray(3) { i -> (colors[i]*level).coerceIn(0.0,1.0).toFloat() }
    }

    /**
     * Real SH projection of a directional analytic hemisphere, as required by
     * Filament's IndirectLight.Builder().radiance(3, coeffs). No pre-baked
     * studio environment maps and no artificial constant RGB ambient light.
     */
    fun skyRadianceSh(elevation:Double,azimuth:Double,haze:Double):FloatArray {
        val coeff=DoubleArray(27)
        val sun=LightDirectionMath.source(azimuth,elevation)
        val bands=12
        val longitudeSegments=24
        for(t in 0 until bands) {
            val theta=PI*(t+.5)/bands
            val y=cos(theta)
            val r=sin(theta)
            for(k in 0 until longitudeSegments) {
                val phi=2.0*PI*(k+.5)/longitudeSegments
                val x=r*cos(phi);val z=r*sin(phi)
                val rgb=skyRadiance(x,y,z,sun.x.toDouble(),sun.y.toDouble(),
                    sun.z.toDouble(),haze)
                val basis=doubleArrayOf(
                    .282095,.488603*y,.488603*z,.488603*x,
                    1.092548*x*y,1.092548*y*z,
                    .315392*(3.0*z*z-1.0),1.092548*x*z,
                    .546274*(x*x-y*y))
                val weight=2.0*PI*PI*r/(bands*longitudeSegments)
                for(i in 0 until 9)for(j in 0 until 3)
                    coeff[3*i+j]+=weight*basis[i]*rgb[j]
            }
        }
        return FloatArray(27) { coeff[it].toFloat() }
    }
}
