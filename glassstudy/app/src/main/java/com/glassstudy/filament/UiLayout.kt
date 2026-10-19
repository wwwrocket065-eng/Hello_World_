package com.glassstudy.filament

import android.graphics.Color
import org.json.JSONObject
import java.nio.charset.StandardCharsets

data class UiButtonSpec(
    val id:String,
    val action:String,
    val slot:String,
    val icon:String,
    val label:String,
    val heightDp:Int,
    val fill:Int?,
    val outline:Int?,
    val textColor:Int?,
    val highlight:Boolean
)

/**
 * Safe, declarative UI layout loaded from bundled assets or a verified .pupdate.
 * The schema can only restyle/reorder existing built-in controls. Never executes code.
 */
data class UiLayout(
    val topColor: Int, val bottomColor: Int, val buttonColor: Int,
    val buttonTextColor: Int, val textColor: Int, val textSizeSp: Float,
    val paddingDp: Int, val actionHeightDp: Int, val gridHeightDp: Int,
    val topOrder: List<String>, val bottomOrder: List<String>,
    val applyText: String, val cameraText: String, val gridText: String,
    val exportText: String, val diagnosticText: String,
    val zoomPower: Double,
    val diagnostics: Map<String, String>,
    val diagnosticSections: List<String>,
    val buttons: List<UiButtonSpec>,
    val showFps:Boolean,
    val panelOpacity:Int,
    val invertHorizontal:Boolean,
    val topButtonSizeDp:Int,
    val topIconScale:Float,
    val showCameraHint:Boolean,
    val showLogoUnderline:Boolean,
    val fillChipCenter:Boolean,
    val immersiveMode:Boolean,
    val systemBarColor:Int,
    val logoColor:Int,
    val subtitleColor:Int,
    val logoSizeDp:Int,
    val logoOffsetYDp:Int,
    val topOverlayOpacity:Int,
    val topButtonOutlineColor:Int,
    val selectorTextColor:Int,
    val fpsTextColor:Int,
    val windows: Map<String, DeclarativeWindow>,
    val animations: Map<String, UiAnimationSpec>,
    val adaptive: AdaptiveUiConfig
) {
    companion object {
        private val DIAGNOSTIC_DEFAULTS = linkedMapOf(
            "title" to "Диагностика GlassStudy",
            "appVersion" to "Версия приложения",
            "installedPatch" to "Установленный патч ресурсов",
            "confirmedPatch" to "Патч, подтверждённый отрисовкой",
            "serverPatch" to "Патч на сервере",
            "updateCheck" to "Состояние обновления",
            "renderer" to "Движок отрисовки",
            "rendererValue" to "Официальный Google Filament / gltfio",
            "geometry" to "Геометрия",
            "geometryValue" to "Одна модель GLB для просмотра и экспорта",
            "device" to "Устройство",
            "android" to "Версия Android",
            "model" to "Модель",
            "screen" to "Разрешение области просмотра",
            "fps" to "Частота кадров (FPS; не нагрузка видеочипа)",
            "glass" to "Физические параметры стекла glTF",
            "glassValue" to "Пропускание=1; IOR=1,52; объём включён",
            "ibl" to "Освещение и отражения IBL",
            "iblAvailable" to "Доступно",
            "iblUnavailable" to "Нет карты окружения; резервный свет",
            "pathTracing" to "Финальная трассировка лучей",
            "pathTracingValue" to "В этой бета-версии не реализована",
            "unknown" to "Не проверено",
            "sectionUpdates" to "ОБНОВЛЕНИЯ",
            "sectionPerformance" to "ПРОИЗВОДИТЕЛЬНОСТЬ",
            "sectionMemory" to "ПАМЯТЬ",
            "sectionScene" to "ГЕОМЕТРИЯ",
            "sectionRenderer" to "РЕНДЕР",
            "sectionCamera" to "КАМЕРА",
            "sectionDevice" to "УСТРОЙСТВО",
            "compactPatches" to "Патчи",
            "patchOrder" to "(установлен / кадр / сервер)",
            "frameTime" to "кадр ср./P95",
            "milliseconds" to "мс",
            "cpuProcess" to "CPU приложения",
            "oneCore" to "от одного ядра",
            "frameSubmission" to "Подготовка/подача кадра",
            "cpuThread" to "время UI-потока",
            "gpuTime" to "GPU (время/нагрузка)",
            "notMeasured" to "не измеряется",
            "appRam" to "Память приложения",
            "deviceRam" to "ОЗУ устройства",
            "freeTotal" to "свободно / всего",
            "megabytes" to "МБ",
            "gridMode" to "сетка",
            "yes" to "ВКЛ",
            "no" to "ВЫКЛ",
            "vertices" to "вершин",
            "triangles" to "треугольников",
            "segments" to "отрезков",
            "primitives" to "меш-прим.",
            "kilobytes" to "КБ",
            "distance" to "Дистанция",
            "yaw" to "поворот",
            "pitch" to "наклон",
            "lightAngles" to "Свет: азимут / высота",
            "gpuInfo" to "Модель GPU",
            "measurementNote" to "FPS — ритм кадров; CPU — процесс (все потоки). GPU-таймер недоступен."
        )
        private val DIAGNOSTIC_SECTIONS =
            listOf("updates","performance","memory","scene","renderer","camera","device")
        // UI actions are strictly whitelisted. Patches can add/remove/reorder
        // button instances but cannot execute new code or start arbitrary intents.
        private val ICONS=setOf(
            "chip","sun","reset","scene","menu","gear","objects","quality","grid","fps","info")
        private val DEFAULT_BUTTONS=listOf(
            UiButtonSpec("diagnostics","diagnostics","top","chip","",44,null,null,null,false),
            UiButtonSpec("light_top","toggleLight","top","sun","",44,null,null,null,false),
            UiButtonSpec("reset","openScene","top","scene","",44,null,null,null,false),
            UiButtonSpec("menu","menu","top","gear","",44,null,null,null,false),
            UiButtonSpec("light_bottom","toggleLight","bottom","sun","Свет / Камера",46,null,null,null,false),
            UiButtonSpec("grid","toggleGrid","bottom","grid","Сетка",46,null,null,null,false)
        )
        private val TOP_ITEMS = listOf("status", "selectors", "actions", "grid")
        private val BOTTOM_ITEMS = listOf("export", "diagnostics")
        fun parse(bytes: ByteArray): UiLayout {
            require(bytes.size in 64..32768) { "UI patch size is invalid" }
            val j=JSONObject(String(bytes, StandardCharsets.UTF_8))
            val windows = DeclarativeWindows.parse(j)
            val animations = UiAnimationEngine.parse(j)
            val adaptive = AdaptiveUiConfig.parse(j)
            val theme = UiThemeEngine.parse(j)
            require(j.getString("format")=="glassstudy-ui-v1" && j.optInt("schema",0)==1) { "Unsupported UI schema" }
            fun color(key: String, fallback: String): Int {
                val hex=j.optString(key,fallback)
                require(Regex("^#[0-9a-fA-F]{6}$").matches(hex)) { "Bad color $key" }
                return theme.color(key, Color.parseColor(hex))
            }
            fun number(key:String, fallback:Double, low:Double, high:Double):Double {
                val value=if(j.has(key))j.getDouble(key) else fallback
                require(value.isFinite() && value in low..high) { "Bad numeric setting $key" }
                return value
            }
            fun text(key:String, fallback:String):String {
                val str=j.optString(key,fallback)
                require(str.length in 1..60 && !str.any { it.isISOControl() }) { "Invalid text $key" }
                return str
            }
            fun order(key:String, accepted:List<String>):List<String> {
                if (!j.has(key)) return accepted
                val a=j.getJSONArray(key)
                require(a.length()==accepted.size) { "Wrong number of UI elements" }
                val names=(0 until a.length()).map{a.getString(it)}
                require(names.toSet()==accepted.toSet() && names.size==names.toSet().size) { "Unknown or duplicate UI component" }
                return names
            }
            val labels=DIAGNOSTIC_DEFAULTS.toMutableMap()
            val supplied=j.optJSONObject("diagnostics")
            require(!j.has("diagnostics") || supplied!=null) { "Invalid diagnostic translations" }
            if (supplied!=null) {
                val names=supplied.keys()
                while (names.hasNext()) {
                    val key=names.next()
                    require(DIAGNOSTIC_DEFAULTS.containsKey(key)) { "Unknown diagnostic label" }
                    val value=supplied.getString(key)
                    require(value.length in 1..130 && !value.any { it.isISOControl() }) {
                        "Invalid diagnostic translation"
                    }
                    labels[key]=value
                }
            }
            val sections=if (!j.has("diagnosticSections")) DIAGNOSTIC_SECTIONS
            else {
                val array=j.optJSONArray("diagnosticSections")
                require(array!=null && array.length() in 1..DIAGNOSTIC_SECTIONS.size) {
                    "Invalid diagnostic section list"
                }
                val requested=(0 until array.length()).map { array.getString(it) }
                require(requested.toSet().size==requested.size &&
                    requested.all { it in DIAGNOSTIC_SECTIONS }) { "Unknown or duplicate diagnostic section" }
                requested
            }
            val specs=if(!j.has("uiButtons")) DEFAULT_BUTTONS else {
                val all=j.getJSONArray("uiButtons")
                require(all.length() in 1..12) { "UI buttons: expected 1..12 elements" }
                val parsed=mutableListOf<UiButtonSpec>()
                for(i in 0 until all.length()) {
                    val obj=all.getJSONObject(i)
                    val id=obj.getString("id")
                    val action=obj.getString("action")
                    val slot=obj.getString("slot")
                    val icon=obj.getString("icon")
                    val label=obj.optString("label","")
                    require(Regex("[a-z][a-zA-Z0-9_]{0,31}").matches(id)) { "Bad button id" }
                    require(UiInteractionRegistry.acceptsAction(action) &&
                        (!action.startsWith("openWindow:") ||
                            windows.containsKey(action.substringAfter(':'))) &&
                        slot in listOf("top","bottom") && icon in ICONS) {
                        "Unknown button action, location or icon"
                    }
                    require(label.length<=40 && !label.any { it.isISOControl() }) { "Bad button label" }
                    require(slot=="top" || label.isNotBlank()) { "Bottom buttons require labels" }
                    val height=obj.optInt("heightDp",46)
                    require(height in 36..72) { "Invalid button height" }
                    fun optionalColor(key:String):Int? {
                        if(!obj.has(key))return null
                        val hex=obj.getString(key)
                        require(Regex("^#[0-9a-fA-F]{6}$").matches(hex)) { "Invalid button color" }
                        return Color.parseColor(hex)
                    }
                    if(obj.optBoolean("visible",true)) parsed.add(
                        UiButtonSpec(id,action,slot,icon,label,height,
                            theme.buttonColor(id,"fill",optionalColor("fill")),
                            theme.buttonColor(id,"outline",optionalColor("outline")),
                            theme.buttonColor(id,"textColor",optionalColor("textColor")),
                            obj.optBoolean("highlight",false))
                    )
                }
                require(parsed.map { it.id }.distinct().size==parsed.size) { "Duplicate UI buttons" }
                require(parsed.count{it.slot=="top"}<=5 && parsed.count{it.slot=="bottom"}<=8) {
                    "Too many buttons for screen"
                }
                require(parsed.any { it.action=="menu" || it.action=="diagnostics" }) {
                    "At least one menu or diagnostics control must remain available"
                }
                parsed
            }
            val showFps=if(j.has("showFps"))j.getBoolean("showFps") else true
            // Old signed UI patches had no switch; invert remains enabled.
            val invertHorizontal=if(j.has("invertHorizontal"))j.getBoolean("invertHorizontal") else true
            val opacity=number("panelOpacity",0.86,0.20,1.0)
            val topSize=number("topButtonSizeDp",35.0,30.0,46.0).toInt()
            val iconScale=number("topIconScale",0.82,0.55,1.0).toFloat()
            val hint=if(j.has("showCameraHint"))j.getBoolean("showCameraHint") else false
            val underline=if(j.has("showLogoUnderline"))j.getBoolean("showLogoUnderline") else false
            val fillChip=if(j.has("fillChipCenter"))j.getBoolean("fillChipCenter") else true
            val immersive=if(j.has("immersiveMode"))j.getBoolean("immersiveMode") else true
            return UiLayout(
                color("topColor","#20313D"),color("bottomColor","#20313D"),
                color("buttonColor","#203E4F"),color("buttonTextColor","#6DE0DE"),
                color("textColor","#FFFFFF"),
                number("textSizeSp",13.0,11.0,20.0).toFloat(),
                number("paddingDp",8.0,0.0,28.0).toInt(),
                number("actionHeightDp",48.0,36.0,76.0).toInt(),
                number("gridHeightDp",42.0,30.0,76.0).toInt(),
                order("topOrder",TOP_ITEMS),order("bottomOrder",BOTTOM_ITEMS),
                text("applyText","Применить"),text("cameraText","Свет / Камера"),
                text("gridText","Сетка: включить / выключить"),text("exportText","Экспорт сцены"),
                text("diagnosticText","Диагностика"),
                number("zoomPower",0.5,0.15,1.25),
                labels.toMap(),sections,
                specs,showFps,(opacity*255).toInt(),invertHorizontal,
                topSize,iconScale,hint,underline,fillChip,immersive,
                color("systemBarColor","#656767"),color("logoColor","#6DE0DE"),
                color("subtitleColor","#9EDCE1"),
                number("logoSizeDp",31.0,24.0,42.0).toInt(),
                number("logoOffsetYDp",5.0,-12.0,12.0).toInt(),
                (number("topOverlayOpacity",0.0,0.0,1.0)*255).toInt(),
                color("topButtonOutlineColor","#6DB9CB"),
                color("selectorTextColor","#A3D0D3"),color("fpsTextColor","#6DE0DE"),windows,animations,adaptive
            )
        }
    }
}
