package com.glassstudy.filament

import org.json.JSONObject

/**
 * Viewport-based layout adjustments, expressed in density-independent pixels.
 * Uses the available application window (not physical screen pixels).
 * A signed layout patch may override sizes without executable code.
 */
data class AdaptiveUiConfig(
    val compactMaxWidthDp: Int = 600,
    val mediumMaxWidthDp: Int = 840,
    val minTouchDp: Int = 44,
    val compactScale: Float = 1f,
    val mediumScale: Float = 1f,
    val expandedScale: Float = 1f
) {
    fun scale(windowWidthDp: Int): Float = when {
        windowWidthDp < compactMaxWidthDp -> compactScale
        windowWidthDp < mediumMaxWidthDp -> mediumScale
        else -> expandedScale
    }
    fun touchSizeDp(requested: Int, windowWidthDp: Int): Int =
        (requested * scale(windowWidthDp)).toInt().coerceAtLeast(minTouchDp)
    companion object {
        fun parse(layout: JSONObject): AdaptiveUiConfig {
            if (!layout.has("adaptive")) return AdaptiveUiConfig()
            val j = layout.getJSONObject("adaptive")
            require(j.keys().asSequence().all { it in setOf("compactMaxWidthDp",
                "mediumMaxWidthDp", "minTouchDp", "compactScale", "mediumScale", "expandedScale") })
            val compact = j.optInt("compactMaxWidthDp", 600)
            val medium = j.optInt("mediumMaxWidthDp", 840)
            val touch = j.optInt("minTouchDp", 44)
            require(compact in 360..720 && medium in (compact + 1)..1200 &&
                touch in 44..72) { "Invalid adaptive breakpoints or touch target" }
            fun factor(key: String): Float {
                val v = if (j.has(key)) j.getDouble(key) else 1.0
                require(v.isFinite() && v in 0.75..1.5) { "Invalid adaptive scale" }
                return v.toFloat()
            }
            return AdaptiveUiConfig(compact, medium, touch, factor("compactScale"),
                factor("mediumScale"), factor("expandedScale"))
        }
    }
}
