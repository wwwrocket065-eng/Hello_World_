package com.glassstudy.filament

import org.json.JSONObject

/**
 * Universal visual glow layer. The same bounded and signed values describe
 * diffuse areas, circles, icons, outlines, line segments or any Canvas Path.
 *
 * For strokes, radiusUnits is glow spread beyond the crisp stroke.
 * For an ambient radial area, radiusUnits is its outer extent.
 */
data class GlowLayerConfig(
    val radiusUnits:Float,
    val opacity:Float,
    val falloff:Float,
    val samples:Int
) {
    companion object {
        fun parse(json:JSONObject?,fallback:GlowLayerConfig):GlowLayerConfig {
            if(json==null)return fallback
            val names=setOf("radiusUnits","opacity","falloff","samples")
            require(json.keys().asSequence().all { it in names }) {
                "Unknown visual glow layer property"
            }
            fun value(key:String,default:Float,min:Float,max:Float):Float {
                val raw=if(json.has(key))json.get(key) else return default
                require(raw is Number) { "Glow parameter is not a number: $key" }
                val n=raw.toFloat()
                require(n.isFinite() && n in min..max) { "Glow parameter out of range: $key" }
                return n
            }
            val count=value("samples",fallback.samples.toFloat(),6f,48f)
            require(count%1f==0f) { "Glow sample count must be integer" }
            return GlowLayerConfig(
                value("radiusUnits",fallback.radiusUnits,0f,96f),
                value("opacity",fallback.opacity,0f,1f),
                value("falloff",fallback.falloff,1f,6f),
                count.toInt()
            )
        }
    }
}

/**
 * Renderer-independent 3-part preset, suitable for ANY UI component.
 * The names deliberately describe visual roles rather than "sun rays".
 * Consumers may bind a button outline, border, icon, or indicator paths
 * to primaryStroke/secondaryStroke and any center to ambient.
 */
data class GlowEffectConfig(
    val primaryStroke:GlowLayerConfig,
    val secondaryStroke:GlowLayerConfig,
    val ambient:GlowLayerConfig
) {
    companion object {
        fun defaults(legacyRadius:Float=10f,legacyOpacity:Float=.42f):GlowEffectConfig {
            val spread=legacyRadius.coerceIn(0f,24f)
            val opacity=if(legacyRadius<=0f)0f else legacyOpacity.coerceIn(0f,1f)
            return GlowEffectConfig(
                GlowLayerConfig(spread+2f,(opacity*1.8f).coerceIn(0f,1f),1.75f,16),
                GlowLayerConfig(spread*.60f,(opacity*.65f).coerceIn(0f,1f),2.0f,14),
                GlowLayerConfig(36f+spread*1.6f,(opacity*.35f).coerceIn(0f,1f),1.6f,24)
            )
        }

        fun parse(json:JSONObject?,fallback:GlowEffectConfig):GlowEffectConfig {
            if(json==null)return fallback
            val keys=setOf("primaryStroke","secondaryStroke","ambient")
            require(json.keys().asSequence().all { it in keys }) {
                "Unknown glow effect component"
            }
            fun child(name:String):JSONObject? {
                if(!json.has(name))return null
                return requireNotNull(json.optJSONObject(name)) {
                    "Glow effect component must be an object: $name"
                }
            }
            return GlowEffectConfig(
                GlowLayerConfig.parse(child("primaryStroke"),fallback.primaryStroke),
                GlowLayerConfig.parse(child("secondaryStroke"),fallback.secondaryStroke),
                GlowLayerConfig.parse(child("ambient"),fallback.ambient)
            )
        }
    }
}
