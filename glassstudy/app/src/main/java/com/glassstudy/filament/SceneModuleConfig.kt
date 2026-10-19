package com.glassstudy.filament

import org.json.JSONObject

/** Declarative signed scene options. Change floor geometry and material by .pupdate. */
data class SceneModuleConfig(
    val floorWidth: Float = 12f,
    val tileWidth: Float = .6f,
    val darkLevel: Double = .006,
    val lightLevel: Double = .96,
    val whiteLevel: Double = .96
) {
    companion object {
        fun parse(json: JSONObject?): SceneModuleConfig {
            if (json == null) return SceneModuleConfig()
            fun number(key:String, fallback:Double, min:Double, max:Double):Double {
                if (!json.has(key)) return fallback
                val value=json.getDouble(key)
                require(value.isFinite() && value in min..max) { "Invalid scene property: $key" }
                return value
            }
            val width=number("floorWidth",12.0,3.0,18.0)
            val tile=number("tileWidth",0.6,0.25,1.5)
            require(width/tile <= 48.0) { "Too many checker tiles" }
            return SceneModuleConfig(width.toFloat(),tile.toFloat(),
                number("darkLevel",.006,0.0,1.0),
                number("lightLevel",.96,0.0,1.0),
                number("whiteLevel",.96,0.0,1.0))
        }
    }
}
