package com.glassstudy.filament

import org.json.JSONObject

/** Diagnostics features enabled by signed configuration; raw measurements remain authoritative. */
data class DiagnosticsModuleConfig(
    val includeModuleStatus: Boolean = false,
    val includeRenderParameters: Boolean = false,
    val includeCameraParameters: Boolean = false
) {
    companion object {
        fun parse(json: JSONObject?): DiagnosticsModuleConfig {
            if (json == null) return DiagnosticsModuleConfig()
            fun flag(name: String): Boolean {
                if (!json.has(name)) return false
                require(json.get(name) is Boolean) { "Expected boolean: $name" }
                return json.getBoolean(name)
            }
            return DiagnosticsModuleConfig(
                includeModuleStatus = flag("includeModuleStatus"),
                includeRenderParameters = flag("includeRenderParameters"),
                includeCameraParameters = flag("includeCameraParameters")
            )
        }
    }
}
