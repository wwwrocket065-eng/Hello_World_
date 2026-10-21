package com.glassstudy.filament

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Single world-space lighting convention shared by Filament and visual sun guides:
 * source points FROM the object TO the sun; light rays and floor shadows travel
 * in the opposite direction. Elevation 0..180 crosses the zenith at 90 degrees.
 */
object LightDirectionMath {
    data class Vector(val x:Float,val y:Float,val z:Float)

    fun source(azimuthRad:Double,elevationRad:Double):Vector {
        require(azimuthRad.isFinite() && elevationRad.isFinite())
        val h=cos(elevationRad)
        return Vector((h*sin(azimuthRad)).toFloat(),sin(elevationRad).toFloat(),
            (h*cos(azimuthRad)).toFloat())
    }

    /** Filament directional LightManager expects direction of incoming rays. */
    fun incoming(azimuthRad:Double,elevationRad:Double):Vector {
        val fromObjectToSun=source(azimuthRad,elevationRad)
        return Vector(-fromObjectToSun.x,-fromObjectToSun.y,-fromObjectToSun.z)
    }

    /** Azimuth wraps continuously; interpolate by at most 180 degrees. */
    fun shortestAzimuthDelta(targetDeg:Float,currentDeg:Float):Float {
        require(targetDeg.isFinite() && currentDeg.isFinite())
        return ((targetDeg-currentDeg+540f)%360f+360f)%360f-180f
    }

    /**
     * Requested arc labels: east horizon 0°, zenith +90°,
     * west horizon -0°; signed return-side values indicate the far side.
     * This changes only the displayed value, never the physical 0..180 input.
     */
    fun elevationLabel(physicalDeg:Float):String {
        val degrees=physicalDeg.coerceIn(0f,180f)
        val display=(if(degrees<=90f)degrees else 180f-degrees)
            .roundToInt().coerceIn(0,90)
        return if(degrees>90f)"-${display}°" else "${display}°"
    }
}
