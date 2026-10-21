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
        val relative=angleRad-cameraYawRad
        return Point(
            (radius*sin(relative)).toFloat(),
            (radius*sin(cameraPitchRad)*cos(relative)).toFloat()
        )
    }

    /**
     * Vertical semicircle in the plane of the current physical light azimuth.
     * Seen edge-on it becomes a line, and obliquely it becomes an ellipse.
     */
    fun elevation(
        angleRad:Double,radius:Float,lightAzimuthRad:Double,
        cameraYawRad:Double,cameraPitchRad:Double
    ):Point {
        val relative=lightAzimuthRad-cameraYawRad
        val planar=cos(angleRad)
        val up=sin(angleRad)
        return Point(
            (radius*planar*sin(relative)).toFloat(),
            (radius*(sin(cameraPitchRad)*planar*cos(relative)-
                cos(cameraPitchRad)*up)).toFloat()
        )
    }
}
