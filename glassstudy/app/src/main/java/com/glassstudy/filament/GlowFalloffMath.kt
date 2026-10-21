package com.glassstudy.filament

import kotlin.math.pow

/**
 * Pure, renderer-independent alpha curves shared by Canvas today and other
 * visual backends later. Endpoints have zero slope: no abrupt aura edge.
 */
object GlowFalloffMath {
    fun alphaAt(distanceFraction:Float,opacity:Float,falloff:Float):Float {
        require(distanceFraction.isFinite() && opacity.isFinite() && falloff.isFinite())
        require(opacity in 0f..1f && falloff>0f)
        val t=distanceFraction.coerceIn(0f,1f)
        val smooth=t*t*(3f-2f*t)
        return opacity*(1f-smooth).pow(falloff)
    }
}
