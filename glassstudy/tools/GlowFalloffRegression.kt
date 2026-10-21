package com.glassstudy.filament

import kotlin.math.abs

/** Standalone: kotlinc GlowFalloffMath.kt GlowFalloffRegression.kt -include-runtime -d glow.jar */
fun main() {
    fun close(a:Float,b:Float,tol:Float=.00005f)=check(abs(a-b)<tol) {
        "Expected $b, actual $a"
    }
    for(gamma in listOf(1f,1.25f,1.75f,2f,4f,6f)) {
        close(GlowFalloffMath.alphaAt(0f,.65f,gamma),.65f)
        close(GlowFalloffMath.alphaAt(1f,.65f,gamma),0f)
        close(GlowFalloffMath.alphaAt(1.001f,.65f,gamma),0f)
        var previous=1f
        for(i in 0..1000) {
            val a=GlowFalloffMath.alphaAt(i/1000f,.65f,gamma)
            check(a in 0f..1f && a<=previous+.000001f)
            previous=a
        }
        // Both sides of the outer boundary fade to zero without a jump.
        val nearEnd=GlowFalloffMath.alphaAt(.999f,.65f,gamma)
        check(nearEnd<.0003f) { "Hard outer glow boundary at gamma=$gamma" }
        // No sharp brightness discontinuity around the middle.
        val midDiff=abs(GlowFalloffMath.alphaAt(.501f,.65f,gamma)-
                        GlowFalloffMath.alphaAt(.5f,.65f,gamma))
        check(midDiff<.004f)
    }
    close(GlowFalloffMath.alphaAt(.5f,0f,1.6f),0f)
    println("PASS: universal glow alpha monotonic, smooth boundary, no hard edge")
}
