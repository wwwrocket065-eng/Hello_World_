package com.glassstudy.filament

import android.view.View
import org.json.JSONObject

/** Validated, data-only animation presets from a signed UI layout patch. */
data class UiAnimationSpec(
    val durationMs: Long = 220,
    val alphaFrom: Float = 0.65f,
    val scaleFrom: Float = 0.94f,
    val rotationFrom: Float = 0f,
    val translateYDp: Float = 0f
)

object UiAnimationEngine {
    private val idPattern = Regex("^[a-z][a-zA-Z0-9_]{0,31}$")
    fun parse(layout: JSONObject): Map<String, UiAnimationSpec> {
        if (!layout.has("animations")) return emptyMap()
        val root = layout.getJSONObject("animations")
        require(root.length() <= 24) { "Too many animations" }
        val result = linkedMapOf<String, UiAnimationSpec>()
        for (id in root.keys()) {
            require(idPattern.matches(id)) { "Invalid animation target" }
            val value = root.getJSONObject(id)
            require(value.keys().asSequence().all {
                it in setOf("durationMs", "alphaFrom", "scaleFrom", "rotationFrom", "translateYDp")
            }) { "Unsupported animation property" }
            fun number(key: String, fallback: Double, min: Double, max: Double): Float {
                val n = if (value.has(key)) value.getDouble(key) else fallback
                require(n.isFinite() && n in min..max) { "Invalid animation value" }
                return n.toFloat()
            }
            result[id] = UiAnimationSpec(
                number("durationMs", 220.0, 0.0, 2000.0).toLong(),
                number("alphaFrom", 0.65, 0.0, 1.0),
                number("scaleFrom", 0.94, 0.5, 1.5),
                number("rotationFrom", 0.0, -360.0, 360.0),
                number("translateYDp", 0.0, -120.0, 120.0)
            )
        }
        return result
    }

    fun enter(view: View, spec: UiAnimationSpec?, density: Float) {
        view.animate().cancel()
        // A cancelled previous animation may leave an already-rendered control
        // partly transparent or scaled when the next preset is disabled.
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.rotation = 0f
        view.translationY = 0f
        if (spec == null || !view.isAttachedToWindow || spec.durationMs == 0L) return
        view.alpha = spec.alphaFrom
        view.scaleX = spec.scaleFrom
        view.scaleY = spec.scaleFrom
        view.rotation = spec.rotationFrom
        view.translationY = spec.translateYDp * density
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .rotation(0f).translationY(0f).setDuration(spec.durationMs).start()
    }
}
