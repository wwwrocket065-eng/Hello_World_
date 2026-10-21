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
    val maxBrightness: Double = 3.0,
    val homeYawRad: Double = 0.56,
    val homePitchRad: Double = 0.34,
    val homeDistance: Double = 5.5,
    val homeLightAzRad: Double = 1.3,
    val homeLightElevationRad: Double = 0.9,
    val minOrbitPitchRad: Double = -0.05,
    val maxOrbitPitchRad: Double = 1.23,
    val brightnessPinchDivisorDp: Double = 160.0,
    val lightAxisLockThresholdDp: Double = 6.0
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
                maxBrightness = v("maxBrightness", 3.0, 0.1, 20.0),
                homeYawRad = v("homeYawRad", 0.56, -6.28319, 6.28319),
                homePitchRad = v("homePitchRad", 0.34, -0.4, 1.5),
                homeDistance = v("homeDistance", 5.5, 0.25, 500.0),
                homeLightAzRad = v("homeLightAzRad", 1.3, 0.0, 6.28319),
                homeLightElevationRad = v("homeLightElevationRad", 0.9, 0.0, Math.PI),
                minOrbitPitchRad = v("minOrbitPitchRad", -0.05, -0.5, 0.5),
                maxOrbitPitchRad = v("maxOrbitPitchRad", 1.23, 0.3, 1.5),
                brightnessPinchDivisorDp = v("brightnessPinchDivisorDp", 160.0, 20.0, 1000.0),
                lightAxisLockThresholdDp = v("lightAxisLockThresholdDp", 6.0, 0.5, 60.0)
            )
            require(config.nearClip < config.farClip && config.minDistance < config.maxDistance &&
                config.minBrightness < config.maxBrightness) { "Camera module range inverted" }
            require(config.minOrbitPitchRad < config.maxOrbitPitchRad &&
                config.homePitchRad in config.minOrbitPitchRad..config.maxOrbitPitchRad &&
                config.homeDistance in config.minDistance..config.maxDistance) {
                "Camera home/orbit configuration is outside validated bounds"
            }
            return config
        }
    }
}
