package com.glassstudy.filament

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/** Standalone pure-Kotlin deterministic checks for the projected 3D guide tracks. */
fun main() {
    fun close(actual:Float,expected:Float,tol:Float=0.002f) {
        check(abs(actual-expected)<=tol) { "expected $expected, got $actual" }
    }
    val r=70f
    // Looking straight down the world azimuth ring => 2D circle.
    val top0=LightGuideProjection.azimuth(0.0,r,0.0,PI/2)
    val top90=LightGuideProjection.azimuth(PI/2,r,0.0,PI/2)
    close(hypot(top0.x,top0.y),r)
    close(hypot(top90.x,top90.y),r)
    // Camera at horizon => ring collapses to a thin horizontal line.
    for(a in listOf(0.0,.52,1.8,PI,5.2)) {
        close(LightGuideProjection.azimuth(a,r,0.0,0.0).y,0f)
    }
    // An oblique camera makes the ring an ellipse, not a shrunken circle.
    close(LightGuideProjection.azimuth(0.0,r,0.0,PI/6).y,r*.5f)
    close(LightGuideProjection.azimuth(PI/2,r,0.0,PI/6).x,r)
    // The azimuth TRACK is frozen to elevation=0°, regardless of the physical
    // solar altitude that drives the glyph and directional shadows.
    close(LightGuideProjection.azimuthOrbit(0.0,r,0.0,PI/6).x,0f)
    val elevations=listOf(0.0,45.0,90.0,135.0,180.0)
    for(deg in elevations) {
        val sun=LightGuideProjection.azimuth(PI/2,r,0.0,PI/6,
            Math.toRadians(deg))
        val fixed=LightGuideProjection.azimuthOrbit(PI/2,r,0.0,PI/6)
        close(fixed.x,r)
        close(fixed.y,0f)
        close(sun.x,(r*kotlin.math.cos(Math.toRadians(deg))).toFloat())
    }
    // Full orbit's diameter and projected height are always those at 0°.
    for(yaw in listOf(0.0,.65,PI)) for(pitch in listOf(.2,PI/6,PI/2)) {
        val pts=(0..72).map {
            LightGuideProjection.azimuthOrbit(it*2*PI/72,r,yaw,pitch)
        }
        val halfWidth=(pts.maxOf { it.x }-pts.minOf { it.x })/2
        val halfHeight=(pts.maxOf { it.y }-pts.minOf { it.y })/2
        // Finite 5-degree sampling of a rotated ellipse differs by up to ~.054 units.
        close(halfWidth,r,.10f)
        close(halfHeight,(r*kotlin.math.sin(pitch)).toFloat(),.10f)
    }
    // For elevation viewed edge-on (camera yaw equals light azimuth), x is zero.
    for(a in listOf(0.0,.5,PI/2,PI)) {
        close(LightGuideProjection.elevation(a,r,.4,.4,.3).x,0f)
    }
    // For side viewing, elevation follows a curved vertical orbit.
    val side=LightGuideProjection.elevation(0.0,r,PI/2,0.0,0.0)
    close(side.x,r);close(side.y,0f)
    val apex=LightGuideProjection.elevation(PI/2,r,PI/2,0.0,0.0)
    close(apex.x,0f);close(apex.y,-r)
    val low=LightGuideProjection.azimuth(PI/2,r,0.0,.35,PI/3)
    val high=LightGuideProjection.azimuth(PI/2,r,0.0,.35,2*PI/3)
    check(low.x>0f && high.x<0f) { "Azimuth orbit must flip past 90deg" }
    close(LightGuideProjection.azimuth(PI/2,r,0.0,.35,PI/2).x,0f)
    println("PASS: azimuth orbit size/height fixed at 0deg, physical sun flips past zenith")
}
