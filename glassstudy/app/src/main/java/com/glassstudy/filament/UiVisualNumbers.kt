package com.glassstudy.filament

import org.json.JSONObject

/** Typed, bounded numeric values shared by reusable UI Chrome views. */
data class UiVisualNumbers(
    val buttonCornerDp:Float=14f,
    val buttonElevationDp:Float=2f,
    val logoHeightFactor:Float=1.22f,
    val selectorClosedTextSp:Float=14f,
    val selectorOpenTextSp:Float=16f,
    val selectorClosedPaddingStartDp:Float=12f,
    val selectorClosedPaddingEndDp:Float=7f,
    val selectorOpenPaddingHorizontalDp:Float=15f,
    val selectorOpenPaddingVerticalDp:Float=10f
) {
    companion object {
        fun parse(json:JSONObject?):UiVisualNumbers {
            if(json==null)return UiVisualNumbers()
            val known=setOf("buttonCornerDp","buttonElevationDp","logoHeightFactor",
                "selectorClosedTextSp","selectorOpenTextSp","selectorClosedPaddingStartDp",
                "selectorClosedPaddingEndDp","selectorOpenPaddingHorizontalDp",
                "selectorOpenPaddingVerticalDp")
            require(json.keys().asSequence().all { it in known }) {
                "Unknown UI visual numeric setting"
            }
            fun n(key:String,default:Double,low:Double,high:Double):Float {
                val value=if(json.has(key))json.getDouble(key) else default
                require(value.isFinite() && value in low..high) {
                    "Invalid UI visual numeric setting: $key"
                }
                return value.toFloat()
            }
            return UiVisualNumbers(
                n("buttonCornerDp",14.0,0.0,32.0),
                n("buttonElevationDp",2.0,0.0,12.0),
                n("logoHeightFactor",1.22,0.8,1.7),
                n("selectorClosedTextSp",14.0,9.0,24.0),
                n("selectorOpenTextSp",16.0,9.0,26.0),
                n("selectorClosedPaddingStartDp",12.0,0.0,24.0),
                n("selectorClosedPaddingEndDp",7.0,0.0,24.0),
                n("selectorOpenPaddingHorizontalDp",15.0,0.0,28.0),
                n("selectorOpenPaddingVerticalDp",10.0,0.0,22.0)
            )
        }
    }
}
