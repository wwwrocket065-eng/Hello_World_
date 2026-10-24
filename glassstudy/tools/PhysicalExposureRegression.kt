package com.glassstudy.filament

import kotlin.math.PI
import kotlin.math.abs

fun main() {
    fun iso(mode:LightEnvironmentMode,angle:Double,base:Double=100.0)=
        PhysicalExposureModel.sensitivity(mode,angle,100000.0,.14,14000.0,
            .08,base,50000.0,32.0)
    fun near(a:Float,b:Float)=check(abs(a-b)<.2f) { "Expected $b, got $a" }
    for(angle in listOf(0.0,PI/4,PI/2,3*PI/4,PI))
        near(iso(LightEnvironmentMode.VACUUM,angle),100f)
    near(iso(LightEnvironmentMode.ATMOSPHERE,0.0),3200f)
    check(iso(LightEnvironmentMode.ATMOSPHERE,PI/4)<=101f)
    check(iso(LightEnvironmentMode.ATMOSPHERE,PI/2)<=101f)
    check(iso(LightEnvironmentMode.ATMOSPHERE,.1) in 100f..3200f)
    check(iso(LightEnvironmentMode.ATMOSPHERE,0.0,600.0)<=12800f)
    near(PhysicalExposureModel.sensitivity(LightEnvironmentMode.ATMOSPHERE,
        0.0,100000.0,.14,14000.0,.08,100.0,50000.0,1.0),100f)
    // Camera exposure never changes the global illumination slider.
    check(LightingResponseCurve.intensity(0.0,1.5)==0.0)
    println("PASS: physical horizon exposure, noon, vacuum, clamp, blackout")
}
