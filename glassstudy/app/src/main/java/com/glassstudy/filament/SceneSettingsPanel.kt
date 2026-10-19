package com.glassstudy.filament

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/**
 * Declarative presentation of signed SceneModuleConfig choices.
 * Scene state and Filament lifecycle are owned by State, not the dialog.
 */
class SceneSettingsPanel(private val activity:Activity,private val state:State) {
    interface State {
        val module:SceneModuleConfig
        val floorMode:String
        val skyMode:String
        val customSkyColor:Int
        fun chooseSky(mode:String)
        fun chooseFloor(mode:String)
        fun chooseCustomSky(color:Int)
    }

    private fun dp(v:Float)=(v*activity.resources.displayMetrics.density+.5f).toInt()

    fun show() {
        val theme=GlassDialogFactory.theme
        val panel=GlassDialogFactory.panel(activity,theme.titles.getValue("scene"))
        panel.addView(TextView(activity).apply {
            text="Пол и окружение настраиваются отдельно"
            textSize=12f;setTextColor(theme.hint)
            setPadding(0,dp(5f),0,0)
        })
        var dialog:AlertDialog?=null
        fun heading(text:String) {
            panel.addView(TextView(activity).apply {
                this.text=text;textSize=13f;setTextColor(theme.accent)
            },LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(17f)})
        }
        fun choice(label:String,selected:Boolean,clicked:()->Unit) {
            panel.addView(GlassDialogFactory.option(activity,label,selected).apply {
                setOnClickListener {
                    dialog?.dismiss()
                    clicked()
                }
            },LinearLayout.LayoutParams(-1,dp(theme.rowHeightDp.toFloat())).apply{
                topMargin=dp(theme.rowSpacingDp.toFloat())
            })
        }
        val skies=mapOf(
            "gray" to "Серый · Blender","studio" to "Студийный",
            "white" to "Белый","dark" to "Тёмный","custom" to "Свой цвет · RGB")
        val floors=mapOf(
            "checker" to "Шахматная доска","white" to "Белая платформа","none" to "Без пола")
        // The signed module controls both section and option order.
        state.module.sectionOrder.forEach { section ->
            when(section) {
                "skybox" -> {
                    heading("SKYBOX")
                    state.module.skyboxOrder.forEach { mode ->
                        choice(skies.getValue(mode),state.skyMode==mode) {
                            if(mode=="custom")showColorPicker()
                            else {state.chooseSky(mode);show()}
                        }
                    }
                }
                "floor" -> {
                    heading("ПОЛ")
                    state.module.floorOrder.forEach { mode ->
                        choice(floors.getValue(mode),state.floorMode==mode) {
                            state.chooseFloor(mode);show()
                        }
                    }
                }
            }
        }
        dialog=GlassDialogFactory.show(activity,ScrollView(activity).apply {
            isFillViewport=false;addView(panel)
        },key="openScene")
    }

    private fun showColorPicker() {
        val theme=GlassDialogFactory.theme
        val panel=GlassDialogFactory.panel(activity,theme.titles.getValue("skyColor"))
        val color=state.customSkyColor
        val values=intArrayOf(Color.red(color),Color.green(color),Color.blue(color))
        val bars=Array(3) { index ->
            SeekBar(activity).apply {
                max=255;progress=values[index]
                progressTintList=ColorStateList.valueOf(theme.accent)
                thumbTintList=ColorStateList.valueOf(theme.accent)
            }.also { seek ->
                panel.addView(TextView(activity).apply {
                    text=listOf("Красный","Зелёный","Синий")[index]
                    textSize=13f;setTextColor(theme.text)
                    setPadding(0,dp(13f),0,0)
                })
                panel.addView(seek)
            }
        }
        var dialog:AlertDialog?=null
        val actions=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
        fun action(title:String,commit:Boolean) {
            val control=GlassDialogFactory.option(activity,title,false)
            control.gravity=Gravity.CENTER
            control.setOnClickListener {
                dialog?.dismiss()
                if(commit) {
                    state.chooseCustomSky(Color.rgb(bars[0].progress,bars[1].progress,bars[2].progress))
                }
                show()
            }
            actions.addView(control,LinearLayout.LayoutParams(0,dp(theme.rowHeightDp.toFloat()),1f).apply {
                setMargins(dp(3f),dp(16f),dp(3f),0)
            })
        }
        action("Отмена",false)
        action("Применить",true)
        panel.addView(actions)
        dialog=GlassDialogFactory.show(activity,panel,key="skyColor")
    }
}
