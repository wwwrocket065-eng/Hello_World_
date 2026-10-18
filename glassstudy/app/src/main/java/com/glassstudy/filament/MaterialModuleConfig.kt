package com.glassstudy.filament

import org.json.JSONObject

/**
 * Signed model/material variant catalog. GLB contains glTF PBR materials and mesh.
 * This does not dynamically compile arbitrary shader code.
 */
data class MaterialModuleConfig(val variants: Map<String, String> = emptyMap()) {
    companion object {
        private val keyPattern = Regex("(faceted|tumbler|wine|mug|pitcher|bowl|vase|sphere|cube|cylinder|cone)_(low|medium|high)(_debug)?")
        private val modelPattern = Regex("models/[a-z][a-z0-9_-]{0,63}(_(low|medium|high))?(_debug)?\\.glb")

        fun parse(json: JSONObject?): MaterialModuleConfig {
            if (json == null) return MaterialModuleConfig()
            val data = json.optJSONObject("variants") ?: return MaterialModuleConfig()
            require(data.length() <= 40) { "Too many material variants" }
            val entries = linkedMapOf<String, String>()
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                require(keyPattern.matches(key)) { "Unsupported material target: $key" }
                val path = data.getString(key)
                require(modelPattern.matches(path) && PatchResourceRegistry.accepts(path)) {
                    "Unsupported material asset: $path"
                }
                entries[key] = path
            }
            return MaterialModuleConfig(entries)
        }
    }

    fun resolve(modelId: String, lod: String, debug: Boolean): String? =
        variants[modelId + "_" + lod + (if (debug) "_debug" else "")]
}
