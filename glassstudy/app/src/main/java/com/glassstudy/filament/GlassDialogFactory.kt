package com.glassstudy.filament

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.PopupWindow
import android.os.Build
import android.view.Gravity
import android.graphics.drawable.GradientDrawable

/** Single presentation entry point for themed modular panels.
 * Window content is built by a feature; modality and dismissal follow one policy.
 */
object GlassDialogFactory {
    fun show(activity: Activity, content: View, widthFraction: Float = .91f): AlertDialog {
        require(widthFraction in .6f..1f)
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.setCancelable(true)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * widthFraction).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }

    /** Common modal presentation for menus, failures and confirmations. */
    fun menu(activity:Activity, title:String, entries:List<String>, onChoice:(Int)->Unit):AlertDialog {
        val content=panel(activity,title)
        var dialog:AlertDialog?=null
        entries.forEachIndexed { index,caption ->
            val item=control(activity,caption)
            item.setOnClickListener { dialog?.dismiss(); onChoice(index) }
            content.addView(item,LinearLayout.LayoutParams(-1,dp(activity,43f)).apply {
                topMargin=dp(activity,7f)
            })
        }
        dialog=show(activity,content)
        return dialog
    }

    fun message(activity:Activity,title:String,body:String):AlertDialog {
        val content=panel(activity,title)
        content.addView(TextView(activity).apply {
            text=body;textSize=13f;setTextColor(Color.rgb(216,229,234))
            setPadding(0,dp(activity,12f),0,dp(activity,10f))
        })
        var dialog:AlertDialog?=null
        val ok=control(activity,"Понятно")
        ok.setOnClickListener { dialog?.dismiss() }
        content.addView(ok,LinearLayout.LayoutParams(-1,dp(activity,43f)))
        dialog=show(activity,content)
        return dialog
    }

    private fun panel(activity:Activity,title:String):LinearLayout =
        LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            val pad=dp(activity,18f)
            setPadding(pad,pad,pad,pad)
            background=background(Color.rgb(17,35,49),20f,Color.rgb(109,185,203))
            addView(TextView(activity).apply {
                text=title;textSize=17f;setTextColor(Color.rgb(163,208,211))
            })
        }

    private fun control(activity:Activity,caption:String):TextView=TextView(activity).apply {
        text=caption;textSize=14f
        gravity=Gravity.CENTER_VERTICAL
        setTextColor(Color.rgb(216,229,234))
        setPadding(dp(activity,14f),0,dp(activity,12f),0)
        background=background(Color.rgb(23,48,64),11f,Color.rgb(59,94,108))
        isClickable=true;isFocusable=true
    }

    private fun background(fill:Int,radius:Float,line:Int):GradientDrawable =
        GradientDrawable().apply {
            shape=GradientDrawable.RECTANGLE
            setColor(fill);cornerRadius=radius;setStroke(1,line)
        }

    private fun dp(activity:Activity,value:Float):Int =
        (activity.resources.displayMetrics.density*value+.5f).toInt()

    /** Non-modal window, sharing the factory lifecycle policy.
     * Used by diagnostics so the toolbar remains interactive.
     */
    fun popup(activity:Activity, content:View, width:Int, onDismiss:()->Unit):PopupWindow {
        require(width>0)
        return PopupWindow(content,width,-2,false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable=true
            isTouchable=true
            isClippingEnabled=true
            if(Build.VERSION.SDK_INT>=29) setTouchModal(false)
            elevation=dp(activity,10f).toFloat()
            setOnDismissListener { onDismiss() }
        }
    }
}
