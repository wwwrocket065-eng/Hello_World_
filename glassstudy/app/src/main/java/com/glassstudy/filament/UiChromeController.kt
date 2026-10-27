package com.glassstudy.filament

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.view.ViewGroup

/** View-only references. The activity retains lifecycle and native component wiring. */
data class UiChromeViews(
    val root:FrameLayout,
    val topPanel:LinearLayout,
    val bottomPanel:LinearLayout,
    val logo:LabLogoView,
    val subtitle:TextView,
    val hint:TextView,
    val fps:TextView,
    val modelSelector:Spinner,
    val qualitySelector:Spinner,
    val toolbar:UiToolbarController,
    val indicator:LightIndicatorView
)

/**
 * One UI presentation owner for the signed layout, toolbar, colors, windows
 * and passive lighting indicator. No renderer, patch activation or model loading.
 * UiThemeEngine resolves tokens in UiLayout before they reach this component.
 */
class UiChromeController(private val activity:Activity) {
    private var attached:UiChromeViews?=null
    var windows:Map<String,DeclarativeWindow> = emptyMap()
        private set
    var animations:Map<String,UiAnimationSpec> = emptyMap()
        private set
    var overlay:OverlayModuleConfig=OverlayModuleConfig()
        private set
    var diagnosticLabels:Map<String,String> = emptyMap()
        private set
    var diagnosticSections:List<String> =
        listOf("updates","performance","memory","scene","renderer","camera","device")
        private set
    private var fillColor=Color.rgb(27,56,75)
    private var selectorColor=Color.rgb(109,224,222)
    private var topOutlineColor=Color.rgb(109,185,203)
    private var iconColor=Color.rgb(163,208,211)
    private var iconSizeDp=35
    private var iconScale=.82f
    private var topVisualSizeDp=35
    private var topVisualScale=1f
    private var visualNumbers=UiVisualNumbers()

    fun dp(v:Float):Int=(v*activity.resources.displayMetrics.density).toInt()

    fun roundedFill(color:Int,radius:Float=15f,
                    outline:Int=Color.rgb(109,224,222),stroke:Float=1f):GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(color,Color.argb(Color.alpha(color),16,39,58))).apply {
            cornerRadius=dp(radius).toFloat()
            setStroke(dp(stroke).coerceAtLeast(1),outline)
        }

    fun skin(view:View,selected:Boolean=false,color:Int=fillColor,outline:Int?=null) {
        val alpha=Color.alpha(color)
        val actual=if(selected)Color.argb(alpha,48,116,124) else color
        val stroke=outline ?:
            if(selected)Color.rgb(166,246,233) else Color.argb(180,109,185,203)
        // Restore the original 0.2.70 control silhouette: every toolbar
        // button shares the same patchable corner profile (14dp by default).
        // Do not invent separate top/bottom or icon-only shapes.
        // Match the original rounded-rectangle silhouette at ANY icon size.
        // A 14dp corner on a 32dp painted square is almost a circle.
        // Apply identical corner proportions to top and bottom icon buttons.
        val visualSideDp=(view as? LabIconView)?.let {
            (it.visualSizeOverrideDp ?: topVisualSizeDp)*topVisualScale
        }
        val cornerDp=visualSideDp?.let { minOf(visualNumbers.buttonCornerDp,it*0.30f) }
            ?: visualNumbers.buttonCornerDp
        val surface=roundedFill(actual,cornerDp,stroke)
        // The four compact TOP glyph buttons keep their full 44dp+ touch target.
        // Only the painted square is inset, independently of adaptive hitbox sizing.
        val content=if(view is LabIconView) {
            val density=activity.resources.displayMetrics.density
            val hitPx=view.layoutParams?.width?.takeIf{it>0}?.toFloat() ?: dp(44f).toFloat()
            val visualPx=dp((view.visualSizeOverrideDp ?: topVisualSizeDp)*topVisualScale).toFloat()
            val inset=((hitPx-visualPx)/2f).coerceAtLeast(0f).toInt()
            InsetDrawable(surface,inset)
        } else surface
        view.background=RippleDrawable(ColorStateList.valueOf(Color.argb(115,93,230,222)),
            content,null)
        view.elevation=dp(visualNumbers.buttonElevationDp).toFloat()
    }

    /** Shared selector view factory; signed theme color is read at render time. */
    fun selectionAdapter(values:Array<String>):ArrayAdapter<String> {
        val adapter=object:ArrayAdapter<String>(
            activity,android.R.layout.simple_spinner_item,values
        ) {
            override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
                val label=super.getView(position,convertView,parent) as TextView
                label.text=values[position]+"  ▾"
                label.textSize=visualNumbers.selectorClosedTextSp
                label.setSingleLine(true)
                label.setTextColor(selectorColor)
                label.setPadding(dp(visualNumbers.selectorClosedPaddingStartDp),0,
                    dp(visualNumbers.selectorClosedPaddingEndDp),0)
                return label
            }
            override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup):View {
                val label=super.getDropDownView(position,convertView,parent) as TextView
                label.text=values[position]
                label.textSize=visualNumbers.selectorOpenTextSp
                label.setTextColor(selectorColor)
                label.setPadding(dp(visualNumbers.selectorOpenPaddingHorizontalDp),
                    dp(visualNumbers.selectorOpenPaddingVerticalDp),
                    dp(visualNumbers.selectorOpenPaddingHorizontalDp),
                    dp(visualNumbers.selectorOpenPaddingVerticalDp))
                label.setBackgroundColor(Color.rgb(24,48,62))
                return label
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }

    fun bind(views:UiChromeViews) {
        check(attached==null) { "UI chrome is already bound" }
        attached=views
    }

    fun popupStyle()=DiagnosticsPopupStyle(
        fill=fillColor,border=topOutlineColor,
        iconColor=iconColor,iconScale=iconScale,iconSizeDp=iconSizeDp
    )

    /** Apply a fully validated snapshot; never parse patch bytes inside a View. */
    fun apply(ui:UiLayout,windowConfig:WindowStyleConfig,newOverlay:OverlayModuleConfig) {
        val v=checkNotNull(attached) { "UI chrome views not bound" }
        windows=ui.windows
        animations=ui.animations
        overlay=newOverlay
        visualNumbers=ui.visualNumbers
        v.indicator.configureVisuals(newOverlay.indicatorVisuals)
        GlassDialogFactory.applyTheme(windowConfig)

        selectorColor=ui.selectorTextColor
        topOutlineColor=ui.topButtonOutlineColor
        iconColor=ui.buttonTextColor
        iconSizeDp=ui.topButtonSizeDp
        topVisualSizeDp=ui.topButtonSizeDp
        topVisualScale=ui.adaptive.scale((activity.resources.displayMetrics.widthPixels / activity.resources.displayMetrics.density).toInt())
        iconScale=ui.topIconScale
        fillColor=ui.buttonColor
        diagnosticLabels=ui.diagnostics
        diagnosticSections=ui.diagnosticSections

        v.logo.showUnderline=ui.showLogoUnderline
        v.logo.logoColor=ui.logoColor
        v.subtitle.setTextColor(ui.subtitleColor)
        val params=v.logo.layoutParams as FrameLayout.LayoutParams
        params.width=dp(ui.logoSizeDp.toFloat())
        params.height=dp(ui.logoSizeDp*visualNumbers.logoHeightFactor)
        params.topMargin=dp(ui.logoOffsetYDp.toFloat())
        params.gravity=Gravity.CENTER
        v.logo.layoutParams=params
        v.fps.setTextColor(ui.fpsTextColor)
        v.hint.visibility=if(ui.showCameraHint)View.VISIBLE else View.GONE
        (v.modelSelector.adapter as? BaseAdapter)?.notifyDataSetChanged()
        (v.qualitySelector.adapter as? BaseAdapter)?.notifyDataSetChanged()
        v.hint.textSize=ui.textSizeSp.coerceIn(11f,14f)
        v.hint.setTextColor(ui.textColor)
        v.fps.visibility=if(ui.showFps)View.VISIBLE else View.GONE
        v.topPanel.background=if(ui.topOverlayOpacity==0)null else
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(ui.topOverlayOpacity,24,34,44),
                    Color.argb(0,24,34,44)))
        v.bottomPanel.background=roundedFill(Color.argb(ui.panelOpacity,35,47,58),
            19f,Color.argb(180,177,218,225))
        v.toolbar.render(ui,animations)
        setSystemBarMode(ui,v.root)
        updateIndicatorVisibility(false)
    }

    fun updateStatus(lightMode:Boolean) {
        val v=attached ?:return
        v.hint.text=if(lightMode)"СВЕТ: палец — направление, два — яркость"
            else "КАМЕРА: палец — вращение, два — масштаб"
        v.toolbar.updateHighlights()
        updateIndicatorVisibility(lightMode)
    }

    fun updateIndicatorVisibility(lightMode:Boolean) {
        val v=attached ?:return
        val px=dp(overlay.indicatorSizeDp.toFloat())
        val layout=v.indicator.layoutParams as FrameLayout.LayoutParams
        if(layout.width!=px || layout.height!=px) {
            layout.width=px
            layout.height=px
            v.indicator.layoutParams=layout
        }
        if(!lightMode || !overlay.enabled || overlay.showWith=="never")
            v.indicator.hide()
    }

    fun showIndicator(mode:LightIndicatorView.Mode,value:Float,lightMode:Boolean,
                      cameraYaw:Double,cameraPitch:Double,lightAzimuth:Double,
                      lightElevation:Double,brightnessPercent:Float) {
        if(!lightMode || !overlay.enabled || overlay.showWith=="never")return
        attached?.indicator?.show(mode,value,overlay.indicatorColor(mode),overlay.guideColor,
            cameraYaw,cameraPitch,lightAzimuth,lightElevation,brightnessPercent,
            overlay.backgroundColor ?: Color.rgb(17,35,49))
    }

    private fun setSystemBarMode(ui:UiLayout,root:FrameLayout) {
        activity.window.statusBarColor=ui.systemBarColor
        activity.window.navigationBarColor=ui.systemBarColor
        if(Build.VERSION.SDK_INT>=30) {
            val controller=activity.window.insetsController
            if(ui.immersiveMode) {
                controller?.hide(WindowInsets.Type.systemBars())
                controller?.systemBarsBehavior=
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else controller?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility=if(ui.immersiveMode) (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) else 0
        }
        root.requestApplyInsets()
    }
}
