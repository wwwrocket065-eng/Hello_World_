package com.glassstudy.filament

import org.json.JSONObject

/** Non-executable, validated controls that may be assembled into a window by a signed patch. */
data class DeclarativeWindowButton(val title: String, val action: String)
data class DeclarativeWindowElement(
    val type: String,
    val label: String,
    val action: String? = null,
    val binding: String? = null,
    val minimum: Float = 5f,
    val maximum: Float = 200f,
    val step: Float = 5f
)
data class DeclarativeWindow(
    val id: String,
    val title: String,
    val buttons: List<DeclarativeWindowButton>,
    val widthFraction: Float? = null,
    val landscapeWidthFraction: Float? = null,
    val elements: List<DeclarativeWindowElement> = emptyList()
)

/**
 * Declarative window schema, from a *verified* .pupdate.
 *
 * Legacy: "buttons":[{"title":"...", "action":"toggleGrid"}]
 * Rich:   "elements":[{"type":"switch","label":"Сетка","binding":"grid"},
 *                     {"type":"slider","label":"Яркость, %","binding":"brightnessPercent",
 *                      "min":5,"max":200,"step":5}]
 *
 * Both syntaxes use an explicit whitelist. There is no script, external URI,
 * arbitrary Android view class, reflection or arbitrary preference access.
 */
object DeclarativeWindows {
    private val idPattern = Regex("^[a-z][a-zA-Z0-9_]{0,31}$")
    private val elementKinds = setOf("heading", "text", "button", "switch", "slider")

    private fun validAction(action: String): Boolean =
        UiInteractionRegistry.acceptsAction(action)

    private fun cleanText(s: String, max: Int): Boolean =
        s.length in 1..max && s.none { it.isISOControl() }

    fun parse(json: JSONObject): Map<String, DeclarativeWindow> {
        if (!json.has("windows")) return emptyMap()
        val windows = json.getJSONArray("windows")
        require(windows.length() <= 12) { "Too many windows" }
        val result = linkedMapOf<String, DeclarativeWindow>()
        repeat(windows.length()) { i ->
            val obj = windows.getJSONObject(i)
            require(obj.keys().asSequence().all {
                it in setOf("id", "title", "buttons", "elements",
                    "widthFraction", "landscapeWidthFraction")
            }) { "Unsupported window property" }
            val id = obj.getString("id")
            val title = obj.getString("title")
            require(idPattern.matches(id) && id !in result) { "Duplicate or invalid window id" }
            require(cleanText(title, 64)) { "Invalid window title" }
            require(obj.has("buttons") != obj.has("elements")) {
                "Exactly one of buttons or elements is required"
            }
            val entries = mutableListOf<DeclarativeWindowButton>()
            val elements = mutableListOf<DeclarativeWindowElement>()
            if (obj.has("buttons")) {
                val buttons = obj.getJSONArray("buttons")
                require(buttons.length() in 1..12) { "Invalid window button count" }
                repeat(buttons.length()) { n ->
                    val button = buttons.getJSONObject(n)
                    require(button.keys().asSequence().all { it in setOf("title", "action") })
                    val text = button.getString("title")
                    val action = button.getString("action")
                    require(cleanText(text, 48) && validAction(action)) { "Invalid window button" }
                    entries.add(DeclarativeWindowButton(text, action))
                    elements.add(DeclarativeWindowElement("button", text, action = action))
                }
            } else {
                val items = obj.getJSONArray("elements")
                require(items.length() in 1..20) { "Invalid window element count" }
                repeat(items.length()) { n ->
                    val item = items.getJSONObject(n)
                    require(item.keys().asSequence().all {
                        it in setOf("type", "label", "action", "binding", "min", "max", "step")
                    }) { "Unknown window element field" }
                    val type = item.getString("type")
                    val label = item.getString("label")
                    require(type in elementKinds && cleanText(label, 96)) { "Invalid window element" }
                    val action = item.optString("action", "").takeIf { it.isNotEmpty() }
                    val binding = item.optString("binding", "").takeIf { it.isNotEmpty() }
                    when (type) {
                        "heading", "text" -> require(action == null && binding == null &&
                            !item.has("min") && !item.has("max") && !item.has("step"))
                        "button" -> {
                            require(action != null && validAction(action) && binding == null &&
                                !item.has("min") && !item.has("max") && !item.has("step"))
                            entries.add(DeclarativeWindowButton(label, action!!))
                        }
                        "switch" -> require(binding != null && UiInteractionRegistry.acceptsBinding(type, binding) && action == null &&
                            !item.has("min") && !item.has("max") && !item.has("step"))
                        "slider" -> require(binding != null && UiInteractionRegistry.acceptsBinding(type, binding) && action == null)
                    }
                    val lo = if (type == "slider") item.optDouble("min", 5.0) else 5.0
                    val hi = if (type == "slider") item.optDouble("max", 200.0) else 200.0
                    val step = if (type == "slider") item.optDouble("step", 5.0) else 5.0
                    require(lo.isFinite() && hi.isFinite() && step.isFinite() &&
                        lo >= 0.0 && hi <= 300.0 && hi > lo &&
                        step >= 0.1 && step <= 50.0 &&
                        (hi - lo) / step <= 1000.0) { "Invalid slider bounds" }
                    elements.add(DeclarativeWindowElement(type, label, action, binding,
                        lo.toFloat(), hi.toFloat(), step.toFloat()))
                }
            }
            val width = if (obj.has("widthFraction")) obj.getDouble("widthFraction") else null
            val landscapeWidth = if (obj.has("landscapeWidthFraction"))
                obj.getDouble("landscapeWidthFraction") else null
            require(width == null || (width.isFinite() && width in 0.65..1.0)) {
                "Invalid window width"
            }
            require(landscapeWidth == null ||
                (landscapeWidth.isFinite() && landscapeWidth in 0.65..1.0)) {
                "Invalid landscape window width"
            }
            result[id] = DeclarativeWindow(id, title, entries,
                width?.toFloat(), landscapeWidth?.toFloat(), elements)
        }
        require(result.values.all { win -> win.elements.all { item ->
            val action = item.action
            action == null || !action.startsWith("openWindow:") ||
                result.containsKey(action.substringAfter(':'))
        } }) { "Unresolved window target" }
        return result
    }
}
