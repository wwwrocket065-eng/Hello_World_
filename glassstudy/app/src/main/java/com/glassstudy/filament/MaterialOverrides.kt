package com.glassstudy.filament

import com.google.android.filament.utils.GlassStudyModelViewer
import org.json.JSONObject

/** Applies optional scalar parameters to loaded glTF material instances. */
data class MaterialOverrides(
    val metallic: Float? = null,
    val roughness: Float? = null,
    val reflectance: Float? = null
) {
    companion object {
        fun parse(json: JSONObject?): MaterialOverrides {
            val values = json?.optJSONObject("overrides") ?: return MaterialOverrides()
            fun scalar(key: String): Float? {
                if (!values.has(key)) return null
                val n = values.getDouble(key)
                require(n.isFinite() && n in 0.0..1.0) { "Invalid material property $key" }
                return n.toFloat()
            }
            return MaterialOverrides(scalar("metallic"), scalar("roughness"), scalar("reflectance"))
        }
    }

    fun apply(viewer: GlassStudyModelViewer) {
        if (metallic == null && roughness == null && reflectance == null) return
        val asset = viewer.asset ?: return
        val manager = viewer.engine.renderableManager
        for (entity in asset.entities) {
            val instance = manager.getInstance(entity)
            if (instance == 0) continue
            for (index in 0 until manager.getPrimitiveCount(instance)) {
                val mat = manager.getMaterialInstanceAt(instance, index)
                metallic?.let { mat.setParameter("metallicFactor", it) }
                roughness?.let { mat.setParameter("roughnessFactor", it) }
                reflectance?.let { mat.setParameter("reflectance", it) }
            }
        }
    }
}
