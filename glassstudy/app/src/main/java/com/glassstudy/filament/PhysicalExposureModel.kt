package com.glassstudy.filament

import kotlin.math.max
import kotlin.math.sin

/**
 * Camera sensitivity adaptation ONLY: never adds fill or changes the physical
 * brightness slider. Estimates outdoor illuminance from the same sun/sky.
 * A fixed daylight exposure loses the horizon's 1120-lux diffuse twilight.
 */
object PhysicalExposureModel {
    fun sensitivity(mode:LightEnvironmentMode,
                    elevation:Double,noonSunLux:Double,extinction:Double,
                    zenithSkyLux:Double,horizonFraction:Double,
                    baseIso:Double,referenceLux:Double,maxGain:Double):Float {
        require(elevation.isFinite() && elevation in 0.0..Math.PI)
        require(noonSunLux.isFinite() && noonSunLux in 100.0..1000000.0)
        require(baseIso.isFinite() && baseIso in 1.0..12800.0)
        require(referenceLux.isFinite() && referenceLux in 1000.0..250000.0)
        require(maxGain.isFinite() && maxGain in 1.0..64.0)
        if(mode==LightEnvironmentMode.VACUUM)return baseIso.toFloat()

        val sky=PhysicalAtmosphereModel.skyIlluminance(mode,elevation,
            zenithSkyLux,horizonFraction)
        val sunHorizontal=noonSunLux*sin(elevation).coerceAtLeast(0.0)*
            PhysicalAtmosphereModel.solarTransmission(mode,elevation,extinction)
        val incident=sky+sunHorizontal
        // ISO is bounded; brightness 0 still extinguishes physical lighting.
        val gain=(referenceLux/max(incident,1.0)).coerceIn(1.0,maxGain)
        return (baseIso*gain).coerceIn(1.0,12800.0).toFloat()
    }
}
