package com.glassstudy.filament

import org.json.JSONObject

/** Patchable, validated camera/lighting controls. Defaults preserve legacy behavior. */
data class CameraModuleConfig(
    val fieldOfView: Double = 45.0,
    val nearClip: Double = 0.05,
    val farClip: Double = 125.0,
    val minDistance: Double = 2.7,
    val maxDistance: Double = 60.0,
    val yawSensitivity: Double = 0.008,
    val pitchSensitivity: Double = 0.005,
    val lightYawSensitivity: Double = 0.009,
    val lightPitchSensitivity: Double = 0.006,
    val lightIntensity: Double = 100000.0,
    val minBrightness: Double = 0.05,
    val maxBrightness: Double = 3.0
) {
    companion object {
        fun parse(json: JSONObject?): CameraModuleConfig {
            if (json == null) return CameraModuleConfig()
            fun v(key: String, default: Double, minimum: Double, maximum: Double): Double {
                if (!json.has(key)) return default
                val value = json.getDouble(key)
                require(value.isFinite() && value in minimum..maximum) { "Invalid camera setting: $key" }
                return value
            }
            val config = CameraModuleConfig(
                fieldOfView = v("fieldOfView", 45.0, 15.0, 110.0),
                nearClip = v("nearClip", 0.05, 0.001, 10.0),
                farClip = v("farClip", 125.0, 20.0, 10000.0),
                minDistance = v("minDistance", 2.7, 0.25, 50.0),
                maxDistance = v("maxDistance", 60.0, 2.0, 500.0),
                yawSensitivity = v("yawSensitivity", 0.008, 0.0001, 0.1),
                pitchSensitivity = v("pitchSensitivity", 0.005, 0.0001, 0.1),
                lightYawSensitivity = v("lightYawSensitivity", 0.009, 0.0001, 0.1),
                lightPitchSensitivity = v("lightPitchSensitivity", 0.006, 0.0001, 0.1),
                lightIntensity = v("lightIntensity", 100000.0, 100.0, 1000000.0),
                minBrightness = v("minBrightness", 0.05, 0.001, 2.0),
                maxBrightness = v("maxBrightness", 3.0, 0.1, 20.0)
            )
            require(config.nearClip < config.farClip && config.minDistance < config.maxDistance &&
                config.minBrightness < config.maxBrightness) { "Camera module range inverted" }
            return config
        }
    }
}
