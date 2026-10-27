package com.glassstudy.filament

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/** Run with pure kotlinc, no Android or Filament engine needed. */
fun main() {
    fun close(actual:Float,expected:Float,tolerance:Float=.003f) {
        check(abs(actual-expected)<tolerance) { "Expected $expected, got $actual" }
    }

    for(az in listOf(0.0,.3,1.7,PI,5.9)) {
        for(elev in listOf(5.0,30.0,89.0,91.0,120.0,175.0)) {
            val rad=Math.toRadians(elev)
            val sun=LightDirectionMath.source(az,rad)
            val rays=LightDirectionMath.incoming(az,rad)
            close(sun.x,-rays.x);close(sun.y,-rays.y);close(sun.z,-rays.z)
            check(rays.y<0f) { "The sun must illuminate the floor from above" }
            // Ray's horizontal projection (cast shadow) is opposite the sun,
            // including past the 90-degree zenith.
            val projected=sun.x*rays.x + sun.z*rays.z
            check(projected <= .00001f)
            check(hypot(sun.x,sun.z) > .05f)
        }
    }
    // A direction vector must flip its horizontal hemisphere over the zenith.
    val before=LightDirectionMath.source(.2,Math.toRadians(70.0))
    val after=LightDirectionMath.source(.2,Math.toRadians(110.0))
    check(before.x*after.x+before.z*after.z<0f)

    close(LightDirectionMath.shortestAzimuthDelta(2f,358f),4f)
    close(LightDirectionMath.shortestAzimuthDelta(358f,2f),-4f)
    close(LightDirectionMath.shortestAzimuthDelta(5f,365f),0f)
    check(LightDirectionMath.elevationLabel(0f)=="0°")
    check(LightDirectionMath.elevationLabel(90f)=="90°")
    check(LightDirectionMath.elevationLabel(120f)=="-60°")
    check(LightDirectionMath.elevationLabel(180f)=="-0°")
    println("PASS: shadow opposes sun past zenith, continuous azimuth, signed elevation")
}
