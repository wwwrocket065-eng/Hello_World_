package com.glassstudy.filament

import org.json.JSONObject

/**
 * Uniform, softly feathered INNER AREA. Independent from outline/ray glow.
 * The center stays constant to (1-edgeSoftness)*radiusUnits;
 * only the narrow boundary smoothly fades to transparent.
 * Generic: suitable for filled circles and icon cores.
 */
data class GlowCoreFillConfig(
    val radiusUnits:Float=10f,
    val opacity:Float=.58f,
    val edgeSoftness:Float=.18f,
    val samples:Int=24
) {
    companion object {
        fun parse(json:JSONObject?,default:GlowCoreFillConfig):GlowCoreFillConfig {
            if(json==null)return default
            val keys=setOf("radiusUnits","opacity","edgeSoftness","samples")
            require(json.keys().asSequence().all { it in keys }) {
                "Unknown glow core fill property"
            }
            fun number(key:String,fallback:Float,lo:Float,hi:Float):Float {
                if(!json.has(key))return fallback
                val raw=json.get(key)
                require(raw is Number) { "Glow core fill is not numeric: $key" }
                val result=raw.toFloat()
                require(result.isFinite() && result in lo..hi) { "Glow core fill out of range: $key" }
                return result
            }
            val samples=number("samples",default.samples.toFloat(),6f,64f)
            require(samples%1f==0f) { "Core fill samples must be integer" }
            return GlowCoreFillConfig(
                number("radiusUnits",default.radiusUnits,0f,32f),
                number("opacity",default.opacity,0f,1f),
                number("edgeSoftness",default.edgeSoftness,.03f,.95f),
                samples.toInt()
            )
        }
    }
}
