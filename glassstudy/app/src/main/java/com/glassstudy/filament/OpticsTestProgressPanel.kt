package com.glassstudy.filament

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Modal input shield; the renderer continues behind a transparent window. */
class OpticsTestProgressPanel(private val activity:Activity, private val onCancel:()->Unit) {
    private var dialog:AlertDialog?=null
    private var details:TextView?=null
    private var cancel:TextView?=null
    fun show() {
        if(dialog!=null)return
        val theme=GlassDialogFactory.theme
        val panel=GlassDialogFactory.panel(activity,"Тест оптики")
        details=TextView(activity).apply {
            textSize=theme.controlTextSizeSp
            setTextColor(theme.text)
        }.also { panel.addView(it) }
        cancel=GlassDialogFactory.option(activity,"Отменить тест",false).apply {
            text="Отменить тест"
            setOnClickListener { onCancel() }
        }.also {
            panel.addView(it,LinearLayout.LayoutParams(-1,
                (theme.rowHeightDp*activity.resources.displayMetrics.density).toInt()))
        }
        dialog=GlassDialogFactory.show(activity,panel,key="opticsTest").apply {
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            window?.apply {
                setGravity(Gravity.BOTTOM)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                setLayout(attributes.width,ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
    }
    fun update(text:String, canCancel:Boolean=true) {
        details?.text=text
        cancel?.isEnabled=canCancel
    }
    fun close() {
        dialog?.dismiss();dialog=null;details=null;cancel=null
    }
}
