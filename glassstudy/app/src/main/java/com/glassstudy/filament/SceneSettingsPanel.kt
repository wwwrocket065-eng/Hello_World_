package com.glassstudy.filament

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Physical light setup. The obsolete studio/flat environment presets are NOT
 * light modes and must not appear in material-research lighting controls.
 */
class SceneSettingsPanel(private val activity:Activity,private val state:State) {
    interface State {
        val module:SceneModuleConfig
        val lightingMode:LightEnvironmentMode
        val floorMode:String
        fun chooseLightingMode(mode:LightEnvironmentMode)
        fun chooseFloor(mode:String)
    }

    private fun dp(v:Float)=(v*activity.resources.displayMetrics.density+.5f).toInt()

    fun show() {
        val theme=GlassDialogFactory.theme
        val panel=GlassDialogFactory.panel(activity,theme.titles.getValue("scene"))
        panel.addView(TextView(activity).apply {
            text="Физическое освещение материалов · без студийной подсветки"
            textSize=12f
            setTextColor(theme.hint)
            setPadding(0,dp(5f),0,0)
        })
        var dialog:AlertDialog?=null
        fun heading(text:String) {
            panel.addView(TextView(activity).apply {
                this.text=text;textSize=13f;setTextColor(theme.accent)
            },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(17f) })
        }
        fun choice(label:String,selected:Boolean,action:()->Unit) {
            panel.addView(GlassDialogFactory.option(activity,label,selected).apply {
                setOnClickListener {
                    dialog?.dismiss()
                    action()
                    show()
                }
            },LinearLayout.LayoutParams(-1,dp(theme.rowHeightDp.toFloat())).apply {
                topMargin=dp(theme.rowSpacingDp.toFloat())
            })
        }
        heading("РЕЖИМ ОСВЕЩЕНИЯ")
        choice("Вакуум · только солнечные лучи",
            state.lightingMode==LightEnvironmentMode.VACUUM) {
            state.chooseLightingMode(LightEnvironmentMode.VACUUM)
        }
        choice("Атмосфера · солнце и рассеянный свет неба",
            state.lightingMode==LightEnvironmentMode.ATMOSPHERE) {
            state.chooseLightingMode(LightEnvironmentMode.ATMOSPHERE)
        }
        heading("ПОЛ")
        val floors=mapOf("checker" to "Шахматная доска",
            "white" to "Белая платформа","none" to "Без пола")
        state.module.floorOrder.forEach { mode ->
            choice(floors.getValue(mode),state.floorMode==mode) {
                state.chooseFloor(mode)
            }
        }
        dialog=GlassDialogFactory.show(activity,ScrollView(activity).apply {
            isFillViewport=false;addView(panel)
        },key="openScene")
    }
}
