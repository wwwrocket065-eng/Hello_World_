package com.glassstudy.filament

import kotlin.math.abs

fun main() {
    for(opacity in listOf(.2f,.58f,1f)) {
        for(softness in listOf(.03f,.18f,.45f,.95f)) {
            val plateau=1f-softness
            for(i in 0..900) {
                val t=plateau*i/900f
                check(abs(GlowCoreFillMath.alphaAt(t,opacity,softness)-opacity)<.00001f) {
                    "Central disk must be UNIFORM: t=$t"
                }
            }
            check(GlowCoreFillMath.alphaAt(1f,opacity,softness)==0f)
            val justBefore=GlowCoreFillMath.alphaAt(.999f,opacity,softness)
            check(justBefore<.004f) { "Abrupt core fill outer edge: $justBefore" }
        }
    }
    println("PASS: uniformly illuminated inner disk; smooth/transparent outer edge")
}
