package com.glassstudy.filament

import org.json.JSONObject

/** Diagnostics features enabled by signed configuration; raw measurements remain authoritative. */
data class DiagnosticsModuleConfig(
    val includeModuleStatus: Boolean = false,
    val includeRenderParameters: Boolean = false,
    val includeCameraParameters: Boolean = false,
    val sampleIntervalMs:Int = 1000,
    val historySeconds:Int = 180,
    val gpuTimingEnabled:Boolean = true
) {
    companion object {
        fun parse(json: JSONObject?): DiagnosticsModuleConfig {
            if (json == null) return DiagnosticsModuleConfig()
            fun flag(name: String,default:Boolean=false): Boolean {
                if (!json.has(name)) return default
                require(json.get(name) is Boolean) { "Expected boolean: $name" }
                return json.getBoolean(name)
            }
            fun bounded(name:String,default:Int,min:Int,max:Int):Int {
                if(!json.has(name))return default
                val value=json.get(name)
                require(value is Number && value.toDouble().isFinite() && value.toDouble()==value.toInt().toDouble()
                    && value.toInt() in min..max) { "Invalid diagnostics.$name" }
                return value.toInt()
            }
            return DiagnosticsModuleConfig(
                includeModuleStatus = flag("includeModuleStatus"),
                includeRenderParameters = flag("includeRenderParameters"),
                includeCameraParameters = flag("includeCameraParameters"),
                sampleIntervalMs=bounded("sampleIntervalMs",1000,250,5000),
                historySeconds=bounded("historySeconds",180,10,600),
                gpuTimingEnabled=flag("gpuTimingEnabled",true)
            )
        }
    }
}
