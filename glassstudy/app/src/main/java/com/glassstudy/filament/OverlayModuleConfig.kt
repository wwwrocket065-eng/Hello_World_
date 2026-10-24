package com.glassstudy.filament

import org.json.JSONObject
import android.graphics.Color

/** Validated signed overlay: only declared controls and built-in safe bindings. */
data class OverlayModuleConfig(
    val enabled:Boolean=true,
    val showWith:String="light",
    val label:String="Яркость освещения",
    val bottomMarginDp:Int=116,
    val widthDp:Int=260,
    val backgroundColor:Int?=null,
    val borderColor:Int?=null,
    val textColor:Int?=null,
    val progressColor:Int?=null,
    val thumbColor:Int?=null,
    val indicatorSizeDp:Int=230,
    val azimuthColor:Int=Color.rgb(240,210,132),
    val elevationColor:Int=Color.rgb(236,225,194),
    val brightnessColor:Int=Color.rgb(255,220,130),
    val guideColor:Int=Color.rgb(173,192,206),
    val indicatorVisuals:Map<LightIndicatorView.Mode,IndicatorVisualConfig> =
        LightIndicatorView.Mode.values().associateWith { IndicatorVisualConfig.defaults(it) }
) {
    /** All three technical indicator suns use a single patchable tint. */
    fun indicatorColor(mode:LightIndicatorView.Mode):Int=brightnessColor
    companion object {
        fun parse(j:JSONObject?):OverlayModuleConfig {
            if(j==null)return OverlayModuleConfig()
            val mode=j.optString("showWith","light")
            require(mode in setOf("light","always","never"))
            val label=j.optString("label","Яркость освещения")
            require(label.length in 1..48 && label.none { it.isISOControl() })
            val bottom=j.optInt("bottomMarginDp",116)
            val width=j.optInt("widthDp",260)
            require(bottom in 32..420 && width in 160..480)
            fun optionalColor(key:String):Int? {
                if(!j.has(key))return null
                val hex=j.getString(key)
                require(Regex("^#[0-9A-Fa-f]{6}$").matches(hex)) { "Invalid overlay color $key" }
                return Color.parseColor(hex)
            }
            val size=j.optInt("indicatorSizeDp",230)
            require(size in 160..340) { "Invalid indicator size" }
            // A single generic numeric schema serves azimuth, elevation and brightness.
            val visuals=j.optJSONObject("indicatorVisuals")
            if(j.has("indicatorVisuals"))require(visuals!=null) { "Invalid indicatorVisuals map" }
            val names=LightIndicatorView.Mode.values().map { it.name.lowercase() }.toSet()
            require(visuals==null || visuals.keys().asSequence().all { it in names }) {
                "Unknown indicator mode"
            }
            val indicatorVisuals=LightIndicatorView.Mode.values().associateWith { indicator ->
                IndicatorVisualConfig.parse(visuals?.optJSONObject(indicator.name.lowercase()),indicator)
            }
            return OverlayModuleConfig(j.optBoolean("enabled",true),mode,label,bottom,width,
                optionalColor("backgroundColor"),optionalColor("borderColor"),
                optionalColor("textColor"),optionalColor("progressColor"),optionalColor("thumbColor"),
                size,
                optionalColor("azimuthColor")?:Color.rgb(240,210,132),
                optionalColor("elevationColor")?:Color.rgb(236,225,194),
                optionalColor("brightnessColor")?:Color.rgb(255,220,130),
                optionalColor("guideColor")?:Color.rgb(173,192,206),
                indicatorVisuals)
        }
    }
}
