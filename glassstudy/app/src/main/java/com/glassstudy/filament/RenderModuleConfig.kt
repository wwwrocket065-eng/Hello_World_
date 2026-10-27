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
    val fxaa: Boolean = false,
    val ambientOcclusionEnabled: Boolean = true,
    val globalBrightnessExponent:Float=1.5f,
    val atmosphereExtinction:Float=.14f,
    val atmosphereHaze:Float=.30f,
    val atmosphereSkyIlluminance:Float=14000f,
    val atmosphereHorizonFraction:Float=.08f,
    val atmosphereShUpdateDegrees:Float=3f,
    val atmosphereExposureReferenceLux:Float=50000f,
    val atmosphereExposureMaxGain:Float=32f,
    val reflectionsEnabled:Boolean=true,
    val reflectionMapSize:Int=64,
    val reflectionSamples:Int=16,
    val reflectionUpdateDegrees:Float=10f
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
            fun integer(name:String, fallback:Int, allowed:Set<Int>):Int {
                if(!json.has(name))return fallback
                val n=json.getDouble(name)
                require(n.isFinite() && n%1.0==0.0 && n in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() &&
                    n.toInt() in allowed) { "Invalid render setting $name" }
                return n.toInt()
            }
            return RenderModuleConfig(
                skyRed = number("skyRed", 0.43f, 0f, 1f),
                skyGreen = number("skyGreen", 0.43f, 0f, 1f),
                skyBlue = number("skyBlue", 0.43f, 0f, 1f),
                iblIntensity = number("iblIntensity", 30000f, 0f, 1000000f),
                exposureAperture = number("exposureAperture", 16f, 0.7f, 64f),
                exposureShutter = number("exposureShutter", 1f / 125f, 0.00001f, 10f),
                exposureSensitivity = number("exposureSensitivity", 100f, 1f, 12800f),
                fxaa = json.optBoolean("fxaa", false),
                ambientOcclusionEnabled = json.optBoolean("ambientOcclusionEnabled", true),
                globalBrightnessExponent = number("globalBrightnessExponent",1.5f,.25f,4f),
                atmosphereExtinction = number("atmosphereExtinction",.14f,.001f,.6f),
                atmosphereHaze = number("atmosphereHaze",.30f,0f,1f),
                atmosphereSkyIlluminance = number("atmosphereSkyIlluminance",14000f,0f,80000f),
                atmosphereHorizonFraction = number("atmosphereHorizonFraction",.08f,0f,.30f),
                atmosphereShUpdateDegrees = number("atmosphereShUpdateDegrees",3f,.5f,12f),
                atmosphereExposureReferenceLux = number("atmosphereExposureReferenceLux",50000f,1000f,250000f),
                atmosphereExposureMaxGain = number("atmosphereExposureMaxGain",32f,1f,64f),
                reflectionsEnabled = json.optBoolean("reflectionsEnabled",true),
                reflectionMapSize = integer("reflectionMapSize",64,setOf(16,32,64,128)),
                reflectionSamples = integer("reflectionSamples",16,setOf(4,8,16,32,64)),
                reflectionUpdateDegrees = number("reflectionUpdateDegrees",10f,3f,45f)
            )
        }
    }
}
