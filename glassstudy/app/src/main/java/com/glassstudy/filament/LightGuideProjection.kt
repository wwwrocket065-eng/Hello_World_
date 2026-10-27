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

    /**
     * Fixed azimuth guide at PHYSICAL elevation 0° (world horizontal XZ).
     * Its radius and projected height depend only on the camera orientation
     * and the signed guide radius, never on the solar altitude. A varying
     * solar elevation must move only the glyph, not shrink the track.
     */
    fun azimuthOrbit(angleRad:Double,radius:Float,
                     cameraYawRad:Double,cameraPitchRad:Double):Point =
        project(LightDirectionMath.source(angleRad,0.0),radius,
            cameraYawRad,cameraPitchRad)

    /**
     * AZIMUTH indicator marker stays attached to the fixed reference circle.
     * It visualizes the solar horizontal *bearing*, not the solar altitude.
     * After 90° the actual sun is on the opposite horizontal hemisphere,
     * so the marker switches sides together with the shadow direction.
     */
    fun azimuth(
        angleRad:Double,radius:Float,cameraYawRad:Double,cameraPitchRad:Double,
        sunElevationRad:Double=0.0
    ):Point {
        return project(LightDirectionMath.azimuthMarkerOnHorizon(
            angleRad,sunElevationRad),radius,cameraYawRad,cameraPitchRad)
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
