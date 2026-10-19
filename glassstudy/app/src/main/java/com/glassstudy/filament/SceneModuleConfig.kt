package com.glassstudy.filament

import org.json.JSONObject

/** Declarative signed scene options. Change floor geometry and material by .pupdate. */
data class SceneModuleConfig(
    val floorWidth: Float = 12f,
    val tileWidth: Float = .6f,
    val darkLevel: Double = .006,
    val lightLevel: Double = .96,
    val whiteLevel: Double = .96,
    val sectionOrder: List<String> = listOf("skybox","floor"),
    val skyboxOrder: List<String> = listOf("gray","studio","white","dark","custom"),
    val floorOrder: List<String> = listOf("checker","white","none")
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
            fun order(key:String, defaults:List<String>):List<String> {
                if (!json.has(key)) return defaults
                val arr=json.getJSONArray(key)
                val chosen=(0 until arr.length()).map { arr.getString(it) }
                require(chosen.size==defaults.size && chosen.toSet()==defaults.toSet()) {
                    "Invalid scene control order: $key"
                }
                return chosen
            }
            return SceneModuleConfig(width.toFloat(),tile.toFloat(),
                number("darkLevel",.006,0.0,1.0),
                number("lightLevel",.96,0.0,1.0),
                number("whiteLevel",.96,0.0,1.0),
                order("sectionOrder",listOf("skybox","floor")),
                order("skyboxOrder",listOf("gray","studio","white","dark","custom")),
                order("floorOrder",listOf("checker","white","none")))
        }
    }
}
