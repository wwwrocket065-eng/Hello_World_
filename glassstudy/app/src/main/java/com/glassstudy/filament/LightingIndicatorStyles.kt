package com.glassstudy.filament

import android.content.Context

/** Validated, persisted presentation preference used by all light indicators. */
object LightingIndicatorStyles {
    data class Option(val id:String,val title:String)
    val options=listOf(
        Option("classic","Классический"),
        Option("minimal","Минимализм"),
        Option("technical","Технический")
    )
    private const val KEY="light_indicator_style"
    fun selected(context:Context):String {
        val stored=context.getSharedPreferences("GlassStudyLighting",Context.MODE_PRIVATE)
            .getString(KEY,"classic") ?: "classic"
        return stored.takeIf { id -> options.any { it.id==id } } ?: "classic"
    }
    fun save(context:Context,id:String) {
        require(options.any { it.id==id }) { "Unknown lighting indicator style" }
        context.getSharedPreferences("GlassStudyLighting",Context.MODE_PRIVATE)
            .edit().putString(KEY,id).apply()
    }
}
