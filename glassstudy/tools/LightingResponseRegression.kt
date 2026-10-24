package com.glassstudy.filament

import kotlin.math.abs

/** Compilable without Android: screen brightness must change smoothly at 0..200. */
fun main() {
    for(exponent in listOf(.25,.5,1.0,1.4,1.5,2.0,3.0,4.0)) {
        val f={p:Double->LightingResponseCurve.intensity(p/100.0,exponent)}
        check(f(0.0)==0.0)
        check(f(100.0)==1.0)
        check(f(200.0)>1.0)
        var prev=0.0
        for(i in 0..2000) {
            val p=i/10.0
            val value=f(p)
            check(value>=prev) { "Brightness reverses between adjacent values at $p" }
            prev=value
        }
        check(f(.001)<.1)
        check(abs(f(.01)-f(0.0))<.25)
    }
    val e=1.5
    check(LightingResponseCurve.intensity(.5,e)<.36)
    check(LightingResponseCurve.intensity(2.0,e)>2.8)
    check(LightingResponseCurve.sky(0.0,1.4,1.25)==0.0)
    check(LightingResponseCurve.sky(.5,1.4,1.25)<.40)
    check(LightingResponseCurve.sky(1.0,1.4,1.25)==1.0)
    check(LightingResponseCurve.sky(2.0,1.4,1.25)==1.25)
    println("PASS: 0..200 continuous brightness, no exact-zero discontinuity")
}
