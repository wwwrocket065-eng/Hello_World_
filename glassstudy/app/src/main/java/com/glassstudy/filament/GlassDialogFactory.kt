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
    var theme:WindowStyleConfig=WindowStyleConfig()
        private set

    fun applyTheme(next:WindowStyleConfig) { theme=next }

    private var activeDialog:AlertDialog?=null
    private var activeKey:String?=null
    fun isOpen(key:String):Boolean = activeKey==key && activeDialog?.isShowing==true
    fun closeCurrent() { activeDialog?.dismiss() }


    fun show(activity: Activity, content: View, widthFraction: Float = theme.widthFraction, key:String?=null): AlertDialog {
        closeCurrent()
        require(widthFraction in .6f..1f)
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.setCancelable(true)
        if(key!=null) {
            activeKey=key
            activeDialog=dialog
            dialog.setOnDismissListener {
                if(activeDialog===dialog) { activeDialog=null;activeKey=null }
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            OrientationLayoutManager.dialogWidthPx(activity.resources.configuration,
                activity.resources.displayMetrics.widthPixels, widthFraction),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }

    /** Common modal presentation for menus, failures and confirmations. */
    fun menu(activity:Activity, title:String, entries:List<String>, animation:UiAnimationSpec?=null, widthFraction:Float=theme.widthFraction, onChoice:(Int)->Unit):AlertDialog {
        val content=panel(activity,title)
        var dialog:AlertDialog?=null
        entries.forEachIndexed { index,caption ->
            val item=control(activity,caption)
            item.setOnClickListener { dialog?.dismiss(); onChoice(index) }
            content.addView(item,LinearLayout.LayoutParams(-1,dp(activity,theme.rowHeightDp.toFloat())).apply {
                topMargin=dp(activity,7f)
            })
        }
        dialog=show(activity,content,widthFraction=widthFraction,key="menu")
        val shown = dialog
        if (animation != null) content.post {
            if (shown?.isShowing == true) UiAnimationEngine.enter(content,animation,activity.resources.displayMetrics.density)
        }
        return dialog
    }

    fun message(activity:Activity,title:String,body:String):AlertDialog {
        val content=panel(activity,title)
        content.addView(TextView(activity).apply {
            text=body;textSize=13f;setTextColor(theme.text)
            setPadding(0,dp(activity,12f),0,dp(activity,10f))
        })
        var dialog:AlertDialog?=null
        val ok=control(activity,"Понятно")
        ok.setOnClickListener { dialog?.dismiss() }
        content.addView(ok,LinearLayout.LayoutParams(-1,dp(activity,theme.rowHeightDp.toFloat())))
        dialog=show(activity,content)
        return dialog
    }

    private fun panel(activity:Activity,title:String):LinearLayout =
        LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            val pad=dp(activity,theme.paddingDp.toFloat())
            setPadding(pad,pad,pad,pad)
            background=background(theme.background,dp(activity,theme.cornerDp).toFloat(),theme.border)
            addView(TextView(activity).apply {
                text=title;textSize=17f;setTextColor(theme.accent)
            })
        }

    private fun control(activity:Activity,caption:String):TextView=TextView(activity).apply {
        text=caption;textSize=14f
        gravity=Gravity.CENTER_VERTICAL
        setTextColor(theme.text)
        setPadding(dp(activity,14f),0,dp(activity,12f),0)
        background=background(theme.control,dp(activity,theme.controlCornerDp).toFloat(),theme.border)
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
