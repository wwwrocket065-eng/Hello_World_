package com.glassstudy.filament

import android.graphics.Color
import org.json.JSONObject
import java.nio.charset.StandardCharsets

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
    val diagnostics: Map<String, String>
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
            "unknown" to "Не проверено"
        )
        private val TOP_ITEMS = listOf("status", "selectors", "actions", "grid")
        private val BOTTOM_ITEMS = listOf("export", "diagnostics")
        fun parse(bytes: ByteArray): UiLayout {
            require(bytes.size in 64..32768) { "UI patch size is invalid" }
            val j=JSONObject(String(bytes, StandardCharsets.UTF_8))
            require(j.getString("format")=="glassstudy-ui-v1" && j.optInt("schema",0)==1) { "Unsupported UI schema" }
            fun color(key: String, fallback: String): Int {
                val hex=j.optString(key,fallback)
                require(Regex("^#[0-9a-fA-F]{6}$").matches(hex)) { "Bad color $key" }
                return Color.parseColor(hex)
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
            return UiLayout(
                color("topColor","#20313D"),color("bottomColor","#20313D"),
                color("buttonColor","#203E4F"),color("buttonTextColor","#FFFFFF"),
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
                labels.toMap()
            )
        }
    }
}
