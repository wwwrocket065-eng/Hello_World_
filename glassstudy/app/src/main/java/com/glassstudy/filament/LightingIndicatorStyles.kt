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

    /** Independent selection for each shape; old global style is fallback. */
    fun selected(context:Context,mode:LightIndicatorView.Mode):String {
        val stored=context.getSharedPreferences("GlassStudyLighting",Context.MODE_PRIVATE)
            .getString("indicator_style_"+mode.name.lowercase(),selected(context)) ?: "classic"
        return stored.takeIf{id -> options.any{it.id==id}} ?: "classic"
    }
    fun save(context:Context,mode:LightIndicatorView.Mode,id:String) {
        require(options.any{it.id==id})
        context.getSharedPreferences("GlassStudyLighting",Context.MODE_PRIVATE).edit()
            .putString("indicator_style_"+mode.name.lowercase(),id).apply()
    }
    fun color(context:Context,mode:LightIndicatorView.Mode,fallback:Int):Int =
        context.getSharedPreferences("GlassStudyLighting",Context.MODE_PRIVATE)
            .getInt("indicator_color_"+mode.name.lowercase(),fallback)
    fun saveColor(context:Context,mode:LightIndicatorView.Mode,color:Int) {
        context.getSharedPreferences("GlassStudyLighting",Context.MODE_PRIVATE).edit()
            .putInt("indicator_color_"+mode.name.lowercase(),color).apply()
    }
    fun reset(context:Context) {
        context.getSharedPreferences("GlassStudyLighting",Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
