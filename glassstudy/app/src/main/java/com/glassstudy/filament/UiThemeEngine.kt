package com.glassstudy.filament

import android.graphics.Color
import org.json.JSONObject

/**
 * Central, declarative color resolver for signed UI layouts.
 *
 * Optional layout JSON:
 * "theme": {
 *   "palette": {"accent": "#9EDCE1", "surface": "#20313D"},
 *   "colors": {"buttonTextColor": "@accent", "topColor": "@surface"},
 *   "buttons": {"diagnostics": {"textColor": "@accent"}}
 * }
 *
 * Explicit legacy values remain supported. No executable code is loaded.
 */
internal class UiThemeEngine private constructor(
    private val palette: Map<String, Int>,
    private val colors: Map<String, Int>,
    private val buttons: Map<String, Map<String, Int>>
) {
    fun color(key: String, fallback: Int): Int = colors[key] ?: fallback
    fun buttonColor(id: String, key: String, fallback: Int?): Int? =
        buttons[id]?.get(key) ?: fallback

    companion object {
        private val tokenName = Regex("^[a-z][a-zA-Z0-9_]{0,31}$")
        private val hexColor = Regex("^#[0-9a-fA-F]{6}$")
        private val allowedColors = setOf(
            "topColor", "bottomColor", "buttonColor", "buttonTextColor",
            "textColor", "systemBarColor", "logoColor", "topButtonOutlineColor",
            "selectorTextColor", "fpsTextColor"
        )
        private val buttonKeys = setOf("fill", "outline", "textColor")

        fun parse(layout: JSONObject): UiThemeEngine {
            if (!layout.has("theme")) return UiThemeEngine(emptyMap(), emptyMap(), emptyMap())
            val theme = layout.optJSONObject("theme")
                ?: throw IllegalArgumentException("theme must be an object")
            require(theme.length() <= 3 && theme.keys().asSequence().all {
                it in setOf("palette", "colors", "buttons")
            }) { "Unknown theme section" }
            val paletteJson = theme.optJSONObject("palette") ?: JSONObject()
            require(!theme.has("palette") || theme.optJSONObject("palette") != null)
            require(paletteJson.length() <= 48) { "Too many theme tokens" }
            val palette = linkedMapOf<String, Int>()
            for (key in paletteJson.keys()) {
                require(tokenName.matches(key)) { "Bad theme token" }
                val value = paletteJson.getString(key)
                require(hexColor.matches(value)) { "Bad theme color" }
                palette[key] = Color.parseColor(value)
            }
            fun resolve(value: String): Int {
                if (value.startsWith("@")) {
                    return palette[value.substring(1)]
                        ?: throw IllegalArgumentException("Unknown theme token")
                }
                require(hexColor.matches(value)) { "Bad theme color" }
                return Color.parseColor(value)
            }
            val colorJson = theme.optJSONObject("colors") ?: JSONObject()
            require(!theme.has("colors") || theme.optJSONObject("colors") != null)
            val colors = linkedMapOf<String, Int>()
            for (key in colorJson.keys()) {
                require(key in allowedColors) { "Unknown theme color target" }
                colors[key] = resolve(colorJson.getString(key))
            }
            val buttonsJson = theme.optJSONObject("buttons") ?: JSONObject()
            require(!theme.has("buttons") || theme.optJSONObject("buttons") != null)
            require(buttonsJson.length() <= 12) { "Too many button overrides" }
            val buttons = linkedMapOf<String, Map<String, Int>>()
            for (id in buttonsJson.keys()) {
                require(tokenName.matches(id)) { "Invalid themed button id" }
                val item = buttonsJson.getJSONObject(id)
                val values = linkedMapOf<String, Int>()
                for (key in item.keys()) {
                    require(key in buttonKeys) { "Unknown button color target" }
                    values[key] = resolve(item.getString(key))
                }
                buttons[id] = values
            }
            return UiThemeEngine(palette, colors, buttons)
        }
    }
}
