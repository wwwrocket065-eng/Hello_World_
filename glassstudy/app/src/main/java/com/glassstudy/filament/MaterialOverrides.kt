package com.glassstudy.filament

import com.google.android.filament.utils.GlassStudyModelViewer
import org.json.JSONObject

/**
 * Only changes parameters actually exposed by the compiled Filament material.
 * Glass transmission/IOR require the GLB's material to support those features.
 */
data class MaterialOverrides(
    val metallic: Float? = null,
    val roughness: Float? = null,
    val reflectance: Float? = null,
    val transmission: Float? = null,
    val ior: Float? = null
) {
    companion object {
        fun parse(json: JSONObject?): MaterialOverrides {
            val values = json?.optJSONObject("overrides") ?: return MaterialOverrides()
            fun number(key: String, low: Double, high: Double): Float? {
                if (!values.has(key)) return null
                val n = values.getDouble(key)
                require(n.isFinite() && n in low..high) { "Invalid material property $key" }
                return n.toFloat()
            }
            return MaterialOverrides(
                metallic = number("metallic", 0.0, 1.0),
                roughness = number("roughness", 0.0, 1.0),
                reflectance = number("reflectance", 0.0, 1.0),
                transmission = number("transmission", 0.0, 1.0),
                ior = number("ior", 1.0, 2.5)
            )
        }
    }

    fun apply(viewer: GlassStudyModelViewer) {
        if (listOf(metallic, roughness, reflectance, transmission, ior).all { it == null }) return
        val asset = viewer.asset ?: return
        val manager = viewer.engine.renderableManager
        for (entity in asset.entities) {
            val instance = manager.getInstance(entity)
            if (instance == 0) continue
            for (index in 0 until manager.getPrimitiveCount(instance)) {
                val materialInstance = manager.getMaterialInstanceAt(instance, index)
                val material = materialInstance.material
                fun setIfSupported(name: String, value: Float?) {
                    if (value != null && material.hasParameter(name)) {
                        materialInstance.setParameter(name, value)
                    }
                }
                setIfSupported("metallicFactor", metallic)
                setIfSupported("roughnessFactor", roughness)
                setIfSupported("reflectance", reflectance)
                setIfSupported("transmissionFactor", transmission)
                setIfSupported("ior", ior)
            }
        }
    }
}
