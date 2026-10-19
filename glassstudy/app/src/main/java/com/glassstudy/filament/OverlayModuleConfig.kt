package com.glassstudy.filament

import org.json.JSONObject

/** Validated signed overlay: only declared controls and built-in safe bindings. */
data class OverlayModuleConfig(
    val enabled:Boolean=true,
    val showWith:String="light",
    val label:String="Яркость освещения",
    val bottomMarginDp:Int=116,
    val widthDp:Int=260
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
            return OverlayModuleConfig(j.optBoolean("enabled",true),mode,label,bottom,width)
        }
    }
}
