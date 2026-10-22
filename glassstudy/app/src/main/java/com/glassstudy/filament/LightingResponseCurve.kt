package com.glassstudy.filament

import kotlin.math.pow

/**
 * One continuous brightness control for directional, indirect and sky lighting.
 * No branch/switch at 0%; percentage 0 always returns 0 and 100 returns 1.
 * The independent exponent lets signed assets calibrate each renderer response.
 */
object LightingResponseCurve {
    fun intensity(factor:Double,exponent:Double):Double {
        require(factor.isFinite() && factor in 0.0..2.0)
        require(exponent.isFinite() && exponent in 0.25..4.0)
        return factor.pow(exponent)
    }

    fun sky(factor:Double,exponent:Double,ceiling:Double):Double {
        require(ceiling.isFinite() && ceiling in 1.0..2.0)
        return intensity(factor,exponent).coerceAtMost(ceiling)
    }
}
