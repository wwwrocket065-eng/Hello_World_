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
    val brightnessOffsetXDp:Float=40f,
    val brightnessRotationAt200Deg:Float=90f,
    val azimuthColor:Int=Color.rgb(240,210,132),
    val elevationColor:Int=Color.rgb(236,225,194),
    val brightnessColor:Int=Color.rgb(255,220,130),
    val guideColor:Int=Color.rgb(173,192,206)
) {
    fun indicatorColor(mode:LightIndicatorView.Mode):Int=when(mode) {
        LightIndicatorView.Mode.AZIMUTH -> azimuthColor
        LightIndicatorView.Mode.ELEVATION -> elevationColor
        LightIndicatorView.Mode.BRIGHTNESS -> brightnessColor
    }
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
            // Signed resource values: bounded to keep the sun, label and guides visible.
            val brightnessOffset=j.optDouble("brightnessOffsetXDp",40.0)
            val brightnessRotation=j.optDouble("brightnessRotationAt200Deg",90.0)
            require(brightnessOffset.isFinite() && brightnessOffset in -60.0..60.0) {
                "Invalid brightness indicator horizontal offset"
            }
            require(brightnessRotation.isFinite() && brightnessRotation in 0.0..180.0) {
                "Invalid brightness sun rotation"
            }
            return OverlayModuleConfig(j.optBoolean("enabled",true),mode,label,bottom,width,
                optionalColor("backgroundColor"),optionalColor("borderColor"),
                optionalColor("textColor"),optionalColor("progressColor"),optionalColor("thumbColor"),
                size,brightnessOffset.toFloat(),brightnessRotation.toFloat(),
                optionalColor("azimuthColor")?:Color.rgb(240,210,132),
                optionalColor("elevationColor")?:Color.rgb(236,225,194),
                optionalColor("brightnessColor")?:Color.rgb(255,220,130),
                optionalColor("guideColor")?:Color.rgb(173,192,206))
        }
    }
}
