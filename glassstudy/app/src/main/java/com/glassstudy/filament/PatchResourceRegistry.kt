package com.glassstudy.filament

import org.json.JSONObject

/**
 * Declarative extension surface for signed .pupdate bundles.
 * No executable DEX, native library or arbitrary filesystem path is accepted.
 * Adding a resource type requires a deliberate consumer in the APK.
 */
object PatchResourceRegistry {
    private val identifier = Regex("[a-z][a-z0-9_-]{0,63}")
    private val moduleConfig = Regex("modules/[a-z][a-z0-9_-]{0,63}/(config|manifest)\\.json")
    private val catalog = Regex("catalogs/[a-z][a-z0-9_-]{0,63}\\.json")
    private val shader = Regex("shaders/[a-z][a-z0-9_-]{0,63}/[a-z][a-z0-9_-]{0,63}\\.(glsl|mat)")
    private val texture = Regex("textures/[a-z][a-z0-9_-]{0,63}/[a-z][a-z0-9_-]{0,63}\\.(png|jpg|ktx|ktx2)")
    private val scene = Regex("scenes/[a-z][a-z0-9_-]{0,63}\\.json")
    private val ui = Regex("ui/[a-z][a-z0-9_-]{0,63}\\.json")
    private val model = Regex("models/[a-z][a-z0-9_-]{0,63}(_(low|medium|high|ultra))?(_debug)?\\.glb")
    private val jsonFormats = setOf("config", "manifest")

    fun accepts(path: String): Boolean {
        if (path.length > 200 || path.startsWith("/") || ".." in path || '\\' in path) return false
        return moduleConfig.matches(path) || catalog.matches(path) || shader.matches(path) ||
            texture.matches(path) || scene.matches(path) || ui.matches(path) || model.matches(path)
    }

    fun validate(path: String, raw: ByteArray) {
        require(accepts(path)) { "Resource path not supported: $path" }
        if (path.endsWith(".glb")) {
            require(raw.size >= 20) { "GLB too small: $path" }
            fun u32(offset: Int): Long =
                (raw[offset].toLong() and 255L) or
                ((raw[offset + 1].toLong() and 255L) shl 8) or
                ((raw[offset + 2].toLong() and 255L) shl 16) or
                ((raw[offset + 3].toLong() and 255L) shl 24)
            require(raw[0] == 0x67.toByte() && raw[1] == 0x6c.toByte() &&
                raw[2] == 0x54.toByte() && raw[3] == 0x46.toByte()) { "GLB magic invalid: $path" }
            require(u32(4) == 2L && u32(8) == raw.size.toLong()) { "GLB header invalid: $path" }
            val jsonLength = u32(12)
            require(jsonLength >= 2L && jsonLength <= raw.size.toLong() - 20L) {
                "GLB JSON chunk invalid: $path"
            }
            require(raw[16] == 0x4a.toByte() && raw[17] == 0x53.toByte() &&
                raw[18] == 0x4f.toByte() && raw[19] == 0x4e.toByte()) {
                "GLB first chunk is not JSON: $path"
            }
        }
        if (path.endsWith(".json")) {
            val json = JSONObject(raw.toString(Charsets.UTF_8))
            require(json.toString().length <= 1024 * 1024) { "JSON resource too large" }
            if (moduleConfig.matches(path)) {
                val parts = path.split("/")
                require(identifier.matches(parts[1]))
                if (parts[2].removeSuffix(".json") in jsonFormats) {
                    require(json.optInt("schema", 1) in 1..2) { "Unsupported module schema" }
                    if (json.has("id")) require(json.getString("id") == parts[1]) { "Module ID mismatch" }
                }
            }
        }
        if (path.endsWith(".glsl") || path.endsWith(".mat")) {
            // Shader resources are stored as assets; compilation is the renderer's responsibility.
            require(raw.none { it == 0.toByte() }) { "Binary shader source" }
        }
    }
}
