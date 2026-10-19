package com.glassstudy.filament

import org.json.JSONObject

/** Data-only windows from a signed UI patch. Never evaluates scripts or arbitrary intents. */
data class DeclarativeWindowButton(val title: String, val action: String)
data class DeclarativeWindow(val id: String, val title: String, val buttons: List<DeclarativeWindowButton>, val widthFraction: Float? = null)

object DeclarativeWindows {
    private val idPattern = Regex("^[a-z][a-zA-Z0-9_]{0,31}$")
    private val actions = setOf("diagnostics", "toggleLight", "resetCamera", "openScene",
        "menu", "selectModel", "selectQuality", "toggleGrid", "toggleFps")
    fun parse(json: JSONObject): Map<String, DeclarativeWindow> {
        if (!json.has("windows")) return emptyMap()
        val windows = json.getJSONArray("windows")
        require(windows.length() <= 12) { "Too many windows" }
        val result = linkedMapOf<String, DeclarativeWindow>()
        repeat(windows.length()) { i ->
            val obj = windows.getJSONObject(i)
            require(obj.keys().asSequence().all { it in setOf("id", "title", "buttons", "widthFraction") })
            val id = obj.getString("id")
            val title = obj.getString("title")
            require(idPattern.matches(id) && !result.containsKey(id)) { "Duplicate or invalid window id" }
            require(title.length in 1..64 && title.none { it.isISOControl() }) { "Invalid window title" }
            val buttons = obj.getJSONArray("buttons")
            require(buttons.length() in 1..12) { "Invalid window button count" }
            val entries = (0 until buttons.length()).map { n ->
                val button = buttons.getJSONObject(n)
                require(button.keys().asSequence().all { it in setOf("title", "action") })
                val text = button.getString("title")
                val action = button.getString("action")
                require(text.length in 1..48 && text.none { it.isISOControl() })
                require(action in actions || (action.startsWith("openWindow:") &&
                    idPattern.matches(action.substringAfter(':')))) { "Unknown window action" }
                DeclarativeWindowButton(text, action)
            }
            val width = if (obj.has("widthFraction")) obj.getDouble("widthFraction") else null
            require(width == null || (width.isFinite() && width in 0.65..1.0)) { "Invalid window width" }
            result[id] = DeclarativeWindow(id, title, entries, width?.toFloat())
        }
        require(result.values.all { win -> win.buttons.all {
            !it.action.startsWith("openWindow:") || result.containsKey(it.action.substringAfter(':'))
        } }) { "Unresolved window target" }
        return result
    }
}
