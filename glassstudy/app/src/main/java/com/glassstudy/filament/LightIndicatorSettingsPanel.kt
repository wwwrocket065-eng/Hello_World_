package com.glassstudy.filament

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Patch-theme-aware style and RGB settings. Native controls, no executable patch content. */
class LightIndicatorSettingsPanel(private val activity:Activity,private val changed:()->Unit) {
    private val modes=listOf(LightIndicatorView.Mode.AZIMUTH,
        LightIndicatorView.Mode.ELEVATION,LightIndicatorView.Mode.BRIGHTNESS)
    private fun name(mode:LightIndicatorView.Mode):String=when(mode) {
        LightIndicatorView.Mode.AZIMUTH -> "Азимут"
        LightIndicatorView.Mode.ELEVATION -> "Высота"
        LightIndicatorView.Mode.BRIGHTNESS -> "Яркость"
    }
    fun show() {
        val entries=modes.map{name(it)+" — стиль"}+
            modes.map{name(it)+" — цвет"}+listOf("Сбросить оформление")
        GlassDialogFactory.menu(activity,"Стиль индикаторов освещения",entries) { pos ->
            when {
                pos<3 -> chooseStyle(modes[pos])
                pos<6 -> chooseColor(modes[pos-3])
                else -> {
                    LightingIndicatorStyles.reset(activity)
                    changed();show()
                }
            }
        }
    }
    private fun chooseStyle(mode:LightIndicatorView.Mode) {
        val variants=LightingIndicatorStyles.options
        GlassDialogFactory.menu(activity,name(mode)+" · стиль",variants.map{it.title}) { index ->
            LightingIndicatorStyles.save(activity,mode,variants[index].id)
            changed();show()
        }
    }
    private fun chooseColor(mode:LightIndicatorView.Mode) {
        val fallback=when(mode) {
            LightIndicatorView.Mode.AZIMUTH -> Color.rgb(240,210,132)
            LightIndicatorView.Mode.ELEVATION -> Color.rgb(236,225,194)
            LightIndicatorView.Mode.BRIGHTNESS -> Color.rgb(255,220,130)
        }
        val tint=LightingIndicatorStyles.color(activity,mode,fallback)
        val channels=intArrayOf(Color.red(tint),Color.green(tint),Color.blue(tint))
        val theme=GlassDialogFactory.theme
        val density=activity.resources.displayMetrics.density
        fun dp(v:Float)=(v*density+.5f).toInt()
        val panel=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18f),dp(18f),dp(18f),dp(18f))
            background=GradientDrawable().apply {
                setColor(theme.background);cornerRadius=dp(theme.cornerDp).toFloat()
                setStroke(dp(1f),theme.border)
            }
        }
        val preview=TextView(activity).apply {
            text=name(mode)+" · RGB"
            gravity=Gravity.CENTER;textSize=17f
            setTextColor(tint);setPadding(0,0,0,dp(12f))
        }
        panel.addView(preview)
        for(index in 0..2) {
            val row=LinearLayout(activity).apply {
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(activity).apply {
                text=listOf("R","G","B")[index];textSize=14f;setTextColor(theme.text)
            },LinearLayout.LayoutParams(dp(22f),-2))
            val control=SeekBar(activity).apply {
                max=255;progress=channels[index]
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(view:SeekBar?,value:Int,fromUser:Boolean) {
                        if(fromUser) {
                            channels[index]=value
                            preview.setTextColor(Color.rgb(channels[0],channels[1],channels[2]))
                        }
                    }
                    override fun onStartTrackingTouch(view:SeekBar?) {}
                    override fun onStopTrackingTouch(view:SeekBar?) {}
                })
            }
            row.addView(control,LinearLayout.LayoutParams(0,dp(48f),1f))
            panel.addView(row)
        }
        var dialog:AlertDialog?=null
        panel.addView(TextView(activity).apply {
            text="Сохранить";textSize=15f;gravity=Gravity.CENTER
            setTextColor(theme.accent)
            setPadding(dp(12f),dp(12f),dp(12f),dp(12f))
            setOnClickListener {
                LightingIndicatorStyles.saveColor(activity,mode,
                    Color.rgb(channels[0],channels[1],channels[2]))
                dialog?.dismiss();changed();show()
            }
        })
        dialog=GlassDialogFactory.show(activity,panel,key="menu")
    }
}
