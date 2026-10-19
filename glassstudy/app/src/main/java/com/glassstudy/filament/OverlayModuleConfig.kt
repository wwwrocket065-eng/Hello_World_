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
    val thumbColor:Int?=null
) {
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
            return OverlayModuleConfig(j.optBoolean("enabled",true),mode,label,bottom,width,
                optionalColor("backgroundColor"),optionalColor("borderColor"),
                optionalColor("textColor"),optionalColor("progressColor"),optionalColor("thumbColor"))
        }
    }
}
