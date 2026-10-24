package com.glassstudy.filament

import kotlin.math.PI
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

    /**
     * Unit horizontal projection of the physically correct solar direction.
     * This places the technical AZIMUTH marker on the fixed 0-degree guide
     * circle, without using the true sun's elevation as the circle radius.
     * The sign of cos(elevation) keeps the marker on the physically correct
     * side of the model after crossing the zenith. Exactly at the zenith the
     * horizontal direction is undefined: retain the approaching azimuth.
     *
     * THIS IS AN INDICATOR PROJECTION, not a second physical light vector.
     */
    fun azimuthMarkerOnHorizon(azimuthRad:Double,elevationRad:Double):Vector {
        val horizon=source(azimuthRad,0.0)
        require(elevationRad.isFinite() && elevationRad in 0.0..PI)
        val side=if(elevationRad>PI/2) -1f else 1f
        return Vector(horizon.x*side,0f,horizon.z*side)
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
