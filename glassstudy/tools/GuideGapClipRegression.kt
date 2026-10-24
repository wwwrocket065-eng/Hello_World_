package com.glassstudy.filament

import kotlin.math.abs

/** Independent JVM regression for moving circular sun/guide gap. */
fun main() {
    fun near(a:Float,b:Float,eps:Float=.002f) {
        check(abs(a-b)<eps) { "Expected $b; got $a" }
    }
    val result=FloatArray(8)  // reused caller buffer, not allocated by clip
    fun clip(ax:Float,ay:Float,bx:Float,by:Float,cx:Float,cy:Float,r:Float)=
        GuideGapClipMath.visibleSegments(ax,ay,bx,by,cx,cy,r,result)

    check(clip(-20f,0f,20f,0f,0f,0f,8f)==2)
    near(result[2],-8f);near(result[4],8f)
    check(clip(-20f,0f,-10f,0f,0f,0f,8f)==1)
    near(result[0],-20f);near(result[2],-10f)
    check(clip(-5f,0f,5f,0f,0f,0f,8f)==0)
    check(clip(0f,0f,20f,0f,0f,0f,8f)==1)
    near(result[0],8f);near(result[2],20f)
    check(clip(-20f,0f,0f,0f,0f,0f,8f)==1)
    near(result[2],-8f)
    check(clip(-20f,8f,20f,8f,0f,0f,8f)==1)  // tangent stays visible
    check(clip(-20f,10f,20f,10f,0f,0f,8f)==1)

    // Continuity while the sun passes: no 2-degree/segment-size jumps.
    var previousEnd=Float.NaN
    for(i in -70..70) {
        val cx=i/10f
        check(clip(-20f,0f,20f,0f,cx,0f,8f)==2)
        val leftEnd=result[2]
        val rightStart=result[4]
        near(leftEnd,cx-8f);near(rightStart,cx+8f)
        if(previousEnd.isFinite())near(leftEnd-previousEnd,.1f)
        previousEnd=leftEnd
    }
    // Both upper and lower circular arc segments work at oblique angles.
    check(clip(-20f,-20f,20f,20f,0f,0f,8f)==2)
    near(kotlin.math.hypot(result[2],result[3]),8f)
    near(kotlin.math.hypot(result[4],result[5]),8f)
    println("PASS: smooth exact guide-circle clipping; no sampled-segment popping")
}
