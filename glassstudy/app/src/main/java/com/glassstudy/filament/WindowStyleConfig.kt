package com.glassstudy.filament

import android.graphics.Color
import org.json.JSONObject

/** Versioned and validated window theme used by all dialog types. */
data class WindowStyleConfig(
    val background:Int=Color.rgb(17,35,49),
    val control:Int=Color.rgb(23,48,64),
    val selected:Int=Color.rgb(31,72,84),
    val border:Int=Color.rgb(109,185,203),
    val accent:Int=Color.rgb(163,208,211),
    val text:Int=Color.rgb(216,229,234),
    val hint:Int=Color.rgb(157,184,195),
    val cornerDp:Float=20f,
    val controlCornerDp:Float=11f,
    val paddingDp:Int=18,
    val rowHeightDp:Int=43,
    val widthFraction:Float=.91f,
    val menuOrder:List<String> = listOf("model","camera","diagnostics"),
    val diagnosticHeaderOrder:List<String> = listOf("title","copy")
) {
    companion object {
        fun parse(json:JSONObject?):WindowStyleConfig {
            if(json==null)return WindowStyleConfig()
            fun color(key:String,default:Int):Int {
                if(!json.has(key))return default
                val value=json.getString(key)
                require(Regex("^#[0-9A-Fa-f]{6}$").matches(value)) { "Invalid window color $key" }
                return Color.parseColor(value)
            }
            fun number(key:String,default:Double,low:Double,high:Double):Float {
                val v=if(json.has(key))json.getDouble(key) else default
                require(v.isFinite() && v in low..high) { "Invalid window size $key" }
                return v.toFloat()
            }
            val menuDefaults=listOf("model","camera","diagnostics")
            val menuOrder=if(json.has("menuOrder")) {
                val array=json.getJSONArray("menuOrder")
                val names=(0 until array.length()).map { array.getString(it) }
                require(names.size==menuDefaults.size && names.toSet()==menuDefaults.toSet()) {
                    "Invalid menu item order"
                }
                names
            } else menuDefaults
            val headerDefaults=listOf("title","copy")
            val headerOrder=if(json.has("diagnosticHeaderOrder")) {
                val array=json.getJSONArray("diagnosticHeaderOrder")
                val names=(0 until array.length()).map { array.getString(it) }
                require(names.size==headerDefaults.size && names.toSet()==headerDefaults.toSet()) {
                    "Invalid diagnostic header order"
                }
                names
            } else headerDefaults
            return WindowStyleConfig(
                color("background",Color.rgb(17,35,49)),
                color("control",Color.rgb(23,48,64)),
                color("selected",Color.rgb(31,72,84)),
                color("border",Color.rgb(109,185,203)),
                color("accent",Color.rgb(163,208,211)),
                color("text",Color.rgb(216,229,234)),
                color("hint",Color.rgb(157,184,195)),
                number("cornerDp",20.0,0.0,36.0),
                number("controlCornerDp",11.0,0.0,28.0),
                number("paddingDp",18.0,4.0,36.0).toInt(),
                number("rowHeightDp",43.0,32.0,64.0).toInt(),
                number("widthFraction",.91,.65,1.0),menuOrder,headerOrder)
        }
    }
}
