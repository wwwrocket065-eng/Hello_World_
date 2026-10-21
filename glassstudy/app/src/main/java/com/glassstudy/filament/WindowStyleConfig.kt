package com.glassstudy.filament

import android.graphics.Color
import org.json.JSONObject

/** Unified validated geometry for the diagnostics popup; data-only signed settings. */
data class DiagnosticsPopupNumbers(
    val horizontalMarginDp:Float=18f,
    val minimumWidthDp:Float=220f,
    val verticalOffsetDp:Float=69f,
    val heightFraction:Float=.63f,
    val panelHorizontalPaddingDp:Float=15f,
    val panelVerticalPaddingDp:Float=14f,
    val headerHeightDp:Float=40f,
    val titleTextSizeSp:Float=15f,
    val reportTextSizeSp:Float=11.5f,
    val reportPaddingDp:Float=10f,
    val reportTopPaddingDp:Float=12f,
    val reportTopMarginDp:Float=10f
) {
    companion object {
        fun parse(json:JSONObject?):DiagnosticsPopupNumbers {
            if(json==null)return DiagnosticsPopupNumbers()
            val valid=setOf("horizontalMarginDp","minimumWidthDp","verticalOffsetDp",
                "heightFraction","panelHorizontalPaddingDp","panelVerticalPaddingDp",
                "headerHeightDp","titleTextSizeSp","reportTextSizeSp","reportPaddingDp",
                "reportTopPaddingDp","reportTopMarginDp")
            require(json.keys().asSequence().all { it in valid }) {
                "Unknown diagnostics popup numeric setting"
            }
            fun n(key:String,default:Double,min:Double,max:Double):Float {
                val value=if(json.has(key))json.getDouble(key) else default
                require(value.isFinite() && value in min..max) {
                    "Invalid diagnostics popup numeric setting: $key"
                }
                return value.toFloat()
            }
            return DiagnosticsPopupNumbers(
                n("horizontalMarginDp",18.0,0.0,64.0),
                n("minimumWidthDp",220.0,120.0,500.0),
                n("verticalOffsetDp",69.0,0.0,220.0),
                n("heightFraction",.63,.2,.85),
                n("panelHorizontalPaddingDp",15.0,0.0,36.0),
                n("panelVerticalPaddingDp",14.0,0.0,36.0),
                n("headerHeightDp",40.0,24.0,80.0),
                n("titleTextSizeSp",15.0,10.0,28.0),
                n("reportTextSizeSp",11.5,8.0,24.0),
                n("reportPaddingDp",10.0,0.0,32.0),
                n("reportTopPaddingDp",12.0,0.0,32.0),
                n("reportTopMarginDp",10.0,0.0,32.0)
            )
        }
    }
}

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
    val titleTextSizeSp:Float=17f,
    val controlTextSizeSp:Float=14f,
    val rowSpacingDp:Int=7,
    val controlHorizontalPaddingDp:Int=14,
    val menuOrder:List<String> = listOf("indicatorStyle"),
    val diagnosticHeaderOrder:List<String> = listOf("title","copy"),
    val titles:Map<String,String> = defaultTitles(),
    val diagnosticsClickMode:String = "toggle",
    val windowClickModes:Map<String,String> = mapOf("diagnostics" to "toggle","openScene" to "toggle","menu" to "toggle"),
    val diagnosticsPopup:DiagnosticsPopupNumbers=DiagnosticsPopupNumbers()
) {
    companion object {
        fun defaultTitles():Map<String,String> = linkedMapOf(
            "scene" to "СЦЕНА", "skyColor" to "ЦВЕТ SKYBOX",
            "diagnostics" to "Диагностика", "mainMenu" to "Настройки",
            "patchInstalled" to "GlassStudy", "patchRejected" to "Патч отклонён",
            "patchError" to "Патч не установлен", "modelError" to "Ошибка загрузки модели")
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
            val menuDefaults=listOf("indicatorStyle")
            val menuOrder=if(json.has("menuOrder")) {
                val array=json.getJSONArray("menuOrder")
                val names=(0 until array.length()).map { array.getString(it) }
                // Legacy signed patches have the old three-item menu.
                // Migrate this data without exposing removed items.
                val legacy=setOf("model","camera","diagnostics")
                if(names.size==3 && names.toSet()==legacy) menuDefaults else {
                    require(names==menuDefaults) { "Invalid menu item order" }
                    names
                }
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
            val defaults=defaultTitles()
            val titles=defaults.toMutableMap()
            if(json.has("titles")) {
                val names=json.getJSONObject("titles")
                for(key in names.keys()) {
                    require(key in defaults) { "Unknown window title key" }
                    val title=names.getString(key)
                    require(title.length in 1..64 && title.none { it.isISOControl() }) {
                        "Invalid window title"
                    }
                    titles[key]=title
                }
            }
            val diagnosticsClickMode=json.optString("diagnosticsClickMode","toggle")
            require(diagnosticsClickMode in setOf("toggle","openOnly","closeOnly")) {
                "Invalid diagnosticsClickMode"
            }
            val defaultWindowModes=mapOf("diagnostics" to "toggle","openScene" to "toggle","menu" to "toggle")
            val windowModes=defaultWindowModes.toMutableMap()
            if(json.has("windowClickModes")) {
                val controls=json.getJSONObject("windowClickModes")
                for(key in controls.keys()) {
                    require(key in defaultWindowModes) { "Unknown window behavior key" }
                    val mode=controls.getString(key)
                    require(mode in setOf("toggle","openOnly","closeOnly")) { "Invalid window behavior" }
                    windowModes[key]=mode
                }
            }
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
                number("widthFraction",.91,.65,1.0),
                number("titleTextSizeSp",17.0,11.0,26.0),
                number("controlTextSizeSp",14.0,10.0,22.0),
                number("rowSpacingDp",7.0,0.0,24.0).toInt(),
                number("controlHorizontalPaddingDp",14.0,0.0,32.0).toInt(),
                menuOrder,headerOrder,titles,diagnosticsClickMode,windowModes,
                DiagnosticsPopupNumbers.parse(json.optJSONObject("diagnosticsPopup")))
        }
    }
}
