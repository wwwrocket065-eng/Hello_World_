package com.glassstudy.filament

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Appearance is a presentation snapshot, not a source of diagnostic measurements. */
data class DiagnosticsPopupStyle(
    val fill:Int, val border:Int, val iconColor:Int,
    val iconScale:Float, val iconSizeDp:Int
)

/** Reusable nonmodal popup lifecycle, sharing the global GlassDialogFactory policy. */
class DiagnosticsPopupController(
    private val activity:Activity,
    private val anchor:View,
    private val onVisibilityChanged:()->Unit
) {
    private var popup:PopupWindow?=null
    var dismissedAtMs:Long=Long.MIN_VALUE
        private set
    val isShowing:Boolean get()=popup?.isShowing==true

    private fun dp(v:Float):Int=(v*activity.resources.displayMetrics.density).toInt()
    fun dismiss() { popup?.dismiss() }

    fun show(report:String,style:DiagnosticsPopupStyle) {
        if(isShowing)return
        val theme=GlassDialogFactory.theme
        val nums=theme.diagnosticsPopup
        val margin=dp(nums.horizontalMarginDp)
        val width=(activity.resources.displayMetrics.widthPixels-margin*2)
            .coerceAtLeast(dp(nums.minimumWidthDp))
        val outer=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(nums.panelHorizontalPaddingDp),dp(nums.panelVerticalPaddingDp),
                dp(nums.panelHorizontalPaddingDp),dp(nums.panelVerticalPaddingDp))
            background=GlassDialogFactory.windowBackground(activity,theme.background,theme.border)
        }
        val header=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val heading=TextView(activity).apply {
            text=theme.titles.getValue("diagnostics")
            textSize=nums.titleTextSizeSp
            setTextColor(theme.accent)
            gravity=Gravity.CENTER_VERTICAL
        }
        val copy=FrameLayout(activity).apply {
            contentDescription="Копировать диагностику"
            isFocusable=true;isClickable=true
            background=GlassDialogFactory.windowBackground(activity,style.fill,style.border,
                theme.controlCornerDp)
        }
        val icon=LabIconView(activity,"copy").apply {
            iconColor=style.iconColor
            glyphScale=style.iconScale
        }
        val size=dp(style.iconSizeDp.toFloat())
        copy.addView(icon,FrameLayout.LayoutParams(size,size,Gravity.CENTER))
        theme.diagnosticHeaderOrder.forEach { item ->
            when(item) {
                "title" -> header.addView(heading,LinearLayout.LayoutParams(0,dp(nums.headerHeightDp),1f))
                "copy" -> header.addView(copy,LinearLayout.LayoutParams(size,size))
            }
        }
        outer.addView(header)
        val reportView=TextView(activity).apply {
            text=report
            setTextColor(theme.text)
            typeface=Typeface.MONOSPACE
            textSize=nums.reportTextSizeSp
            setTextIsSelectable(true)
            setPadding(dp(nums.reportPaddingDp),dp(nums.reportTopPaddingDp),
                dp(nums.reportPaddingDp),dp(nums.reportPaddingDp))
        }
        val scrolling=ScrollView(activity).apply {
            isFillViewport=false
            background=GlassDialogFactory.windowBackground(activity,Color.rgb(5,23,36),
                Color.argb(100,100,155,165),8f)
            addView(reportView)
        }
        outer.addView(scrolling,LinearLayout.LayoutParams(-1,
            (activity.resources.displayMetrics.heightPixels*nums.heightFraction).toInt()).apply {
            topMargin=dp(nums.reportTopMarginDp)
        })
        val shown=GlassDialogFactory.popup(activity,outer,width) {
            dismissedAtMs=SystemClock.uptimeMillis()
            popup=null
            onVisibilityChanged()
        }
        copy.setOnClickListener {
            val clipboard=activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Диагностика GlassStudy",report))
            Toast.makeText(activity,"Скопировано",Toast.LENGTH_SHORT).show()
        }
        popup=shown
        // The four top toolbar icons remain accessible.
        shown.showAtLocation(anchor,Gravity.TOP or Gravity.CENTER_HORIZONTAL,0,dp(nums.verticalOffsetDp))
        onVisibilityChanged()
    }
}
