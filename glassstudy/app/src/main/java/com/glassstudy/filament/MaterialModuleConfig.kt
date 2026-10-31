package com.glassstudy.filament

import org.json.JSONObject

/**
 * Signed model/material variant catalog. GLB contains glTF PBR materials and mesh.
 * This does not dynamically compile arbitrary shader code.
 */
data class MaterialModuleConfig(val variants: Map<String, String> = emptyMap(),
    val primitiveRadius:Float=.8f,val primitiveHeight:Float=1.6f,
    val primitiveWireWidth:Float=.0016f,val primitiveWireOffset:Float=.003f) {
    companion object {
        private val keyPattern = Regex("(faceted|tumbler|wine|goblet|mug|pitcher|bowl|vase|sphere|cube|cylinder|cone)_(low|medium|high|ultra)(_debug)?")
        private val modelPattern = Regex("models/[a-z][a-z0-9_-]{0,63}(_(low|medium|high|ultra))?(_debug)?\\.glb")

        fun parse(json: JSONObject?): MaterialModuleConfig {
            if (json == null) return MaterialModuleConfig()
            fun number(key:String,default:Float,low:Float,high:Float):Float {
                if(!json.has(key))return default
                val value=json.get(key)
                require(value is Number)
                val n=value.toFloat();require(n.isFinite() && n in low..high)
                return n
            }
            val radius=number("primitiveRadius",.8f,.1f,2f)
            val height=number("primitiveHeight",1.6f,.2f,4f)
            val width=number("primitiveWireWidth",.0016f,.0003f,.01f)
            val offset=number("primitiveWireOffset",.003f,.0005f,.02f)
            val data = json.optJSONObject("variants") ?: JSONObject()
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
            return MaterialModuleConfig(entries,radius,height,width,offset)
        }
    }

    fun resolve(modelId: String, lod: String, debug: Boolean): String? =
        variants[modelId + "_" + lod + (if (debug) "_debug" else "")]
}
