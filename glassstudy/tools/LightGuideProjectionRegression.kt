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
    // Both guides are fixed relative to the camera view, not driven by glyph rotation.
    close(LightGuideProjection.azimuth(0.0,r,0.0,PI/6).x,0f)
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
    println("PASS: 3D-projected lighting guides, fixed circular glyph")
}
