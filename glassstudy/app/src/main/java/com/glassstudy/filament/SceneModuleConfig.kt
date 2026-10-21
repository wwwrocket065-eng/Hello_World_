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
    val floorOrder: List<String> = listOf("checker","white","none"),
    val graySkyRed:Int=110,
    val graySkyGreen:Int=110,
    val graySkyBlue:Int=110,
    val whiteSkyRed:Int=240,
    val whiteSkyGreen:Int=240,
    val whiteSkyBlue:Int=240,
    val darkSkyRed:Int=27,
    val darkSkyGreen:Int=30,
    val darkSkyBlue:Int=35
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
            fun channel(name:String,default:Int):Int {
                val value=number(name,default.toDouble(),0.0,255.0)
                require(value%1.0==0.0) { "Scene RGB channel must be integer: $name" }
                return value.toInt()
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
                order("floorOrder",listOf("checker","white","none")),
                channel("graySkyRed",110),channel("graySkyGreen",110),channel("graySkyBlue",110),
                channel("whiteSkyRed",240),channel("whiteSkyGreen",240),channel("whiteSkyBlue",240),
                channel("darkSkyRed",27),channel("darkSkyGreen",30),channel("darkSkyBlue",35))
        }
    }
}
