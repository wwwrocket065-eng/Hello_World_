package com.glassstudy.filament

/**
 * Pure uniform-inner-disk alpha curve, independent of any renderer.
 * The core keeps one brightness then softly fades at its outermost edge.
 */
object GlowCoreFillMath {
    fun alphaAt(fraction:Float,opacity:Float,edgeSoftness:Float):Float {
        require(fraction.isFinite() && opacity.isFinite() && edgeSoftness.isFinite())
        require(opacity in 0f..1f && edgeSoftness>=.03f && edgeSoftness<=.95f)
        val t=fraction.coerceIn(0f,1f)
        val start=1f-edgeSoftness
        if(t<=start)return opacity
        val edge=((t-start)/edgeSoftness).coerceIn(0f,1f)
        val smooth=edge*edge*(3f-2f*edge)
        return opacity*(1f-smooth)
    }
}
