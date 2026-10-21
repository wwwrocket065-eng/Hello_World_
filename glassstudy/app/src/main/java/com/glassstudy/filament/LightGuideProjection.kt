package com.glassstudy.filament

import kotlin.math.cos
import kotlin.math.sin

/**
 * Orthographic projection of world-fixed horizontal and vertical light
 * orbits from the same yaw/pitch as the Filament orbit camera.
 *
 * Only the GUIDE and orbital LOCATION are projected. The sun glyph stays a
 * screen-facing circle with eight undistorted rays.
 */
object LightGuideProjection {
    data class Point(val x:Float,val y:Float)

    /** Horizontal world XZ circle; a top-down view is round, horizon is a line. */
    fun azimuth(
        angleRad:Double,radius:Float,cameraYawRad:Double,cameraPitchRad:Double
    ):Point {
        return project(LightDirectionMath.source(angleRad,0.0),radius,
            cameraYawRad,cameraPitchRad)
    }

    /**
     * Vertical semicircle in the plane of the current physical light azimuth.
     * Seen edge-on it becomes a line, and obliquely it becomes an ellipse.
     */
    fun elevation(
        angleRad:Double,radius:Float,lightAzimuthRad:Double,
        cameraYawRad:Double,cameraPitchRad:Double
    ):Point {
        return project(LightDirectionMath.source(lightAzimuthRad,angleRad),radius,
            cameraYawRad,cameraPitchRad)
    }
    /** The same 3D light-source vector drives both HUD and Filament shading. */
    private fun project(source:LightDirectionMath.Vector,radius:Float,
                        cameraYawRad:Double,cameraPitchRad:Double):Point {
        val right=source.x*cos(cameraYawRad)-source.z*sin(cameraYawRad)
        val forward=source.x*sin(cameraYawRad)+source.z*cos(cameraYawRad)
        return Point((radius*right).toFloat(),
            (radius*(sin(cameraPitchRad)*forward-
                cos(cameraPitchRad)*source.y)).toFloat())
    }
}
