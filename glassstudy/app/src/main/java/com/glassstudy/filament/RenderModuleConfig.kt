package com.glassstudy.filament

import org.json.JSONObject

/** Renderer settings supplied by a signed modular asset, with safe legacy defaults. */
data class RenderModuleConfig(
    val skyRed: Float = 0.43f,
    val skyGreen: Float = 0.43f,
    val skyBlue: Float = 0.43f,
    val iblIntensity: Float = 30000f,
    val exposureAperture: Float = 16f,
    val exposureShutter: Float = 1f / 125f,
    val exposureSensitivity: Float = 100f,
    val fxaa: Boolean = true,
    val ambientOcclusionEnabled: Boolean = true
) {
    companion object {
        fun parse(json: JSONObject?): RenderModuleConfig {
            if (json == null) return RenderModuleConfig()
            fun number(name: String, fallback: Float, min: Float, max: Float): Float {
                if (!json.has(name)) return fallback
                val n = json.getDouble(name)
                require(n.isFinite() && n >= min && n <= max) { "Invalid render setting $name" }
                return n.toFloat()
            }
            return RenderModuleConfig(
                skyRed = number("skyRed", 0.43f, 0f, 1f),
                skyGreen = number("skyGreen", 0.43f, 0f, 1f),
                skyBlue = number("skyBlue", 0.43f, 0f, 1f),
                iblIntensity = number("iblIntensity", 30000f, 0f, 1000000f),
                exposureAperture = number("exposureAperture", 16f, 0.7f, 64f),
                exposureShutter = number("exposureShutter", 1f / 125f, 0.00001f, 10f),
                exposureSensitivity = number("exposureSensitivity", 100f, 1f, 12800f),
                fxaa = json.optBoolean("fxaa", true),
                ambientOcclusionEnabled = json.optBoolean("ambientOcclusionEnabled", true)
            )
        }
    }
}
