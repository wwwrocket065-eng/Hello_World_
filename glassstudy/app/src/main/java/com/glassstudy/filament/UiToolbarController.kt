package com.glassstudy.filament

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Reusable, action-agnostic toolbar renderer. Only Host knows app state and
 * side effects. All declarative buttons use identical layout/animation paths.
 */
class UiToolbarController(
    private val activity:Activity,
    private val topHost:LinearLayout,
    private val bottomHost:LinearLayout,
    private val widthSource:View,
    private val host:Host
) {
    interface Host {
        fun dispatch(action:String)
        fun isActive(action:String):Boolean
        fun decorate(view:View,selected:Boolean,color:Int,outline:Int?)
        fun onLightLongPress()
    }
    private var layout:UiLayout?=null
    private val buttons=linkedMapOf<String,View>()
    private fun dp(v:Float):Int=(v*activity.resources.displayMetrics.density).toInt()

    fun updateHighlights() {
        val ui=layout?:return
        ui.buttons.forEach { spec ->
            buttons[spec.id]?.let { view ->
                host.decorate(view,spec.highlight||host.isActive(spec.action),
                    spec.fill?:ui.buttonColor,
                    spec.outline?:if(spec.slot=="top")ui.topButtonOutlineColor else null)
            }
        }
    }
    private fun button(spec:UiButtonSpec,ui:UiLayout,animations:Map<String,UiAnimationSpec>):View {
        val ink=spec.textColor?:ui.buttonTextColor
        val target:View=if(spec.slot=="top") {
            LabIconView(activity,spec.icon).apply {
                iconColor=ink
                glyphScale=ui.topIconScale
                gearInnerRoundness=ui.gearInnerRoundness
                chipCenterFilled=ui.fillChipCenter
                contentDescription=when(spec.action) {
                    "diagnostics" -> "Диагностика"
                    "toggleLight" -> "Свет / Камера"
                    "resetCamera" -> "Сброс камеры"
                    "openScene" -> "Сцена"
                    "menu" -> "Настройки"
                    else -> spec.label.ifBlank { spec.action }
                }
            }
        } else {
            LinearLayout(activity).apply {
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER
                addView(LabIconView(activity,spec.icon).apply {
                    iconColor=ink
                    glyphScale=.80f
                    chipCenterFilled=ui.fillChipCenter
                },LinearLayout.LayoutParams(dp(25f),dp(25f)))
                addView(TextView(activity).apply {
                    text=spec.label
                    textSize=if(spec.label.length>22)12f else 14f
                    setTextColor(ink)
                    gravity=Gravity.CENTER
                    isSingleLine=true
                    setPadding(dp(5f),0,0,0)
                })
                contentDescription=spec.label
            }
        }
        target.isClickable=true
        target.isFocusable=true
        target.setOnClickListener {
            UiAnimationEngine.enter(target,animations[spec.id],
                activity.resources.displayMetrics.density)
            host.dispatch(spec.action)
        }
        if(spec.action=="toggleLight")target.setOnLongClickListener {
            host.onLightLongPress()
            true
        }
        host.decorate(target,spec.highlight||host.isActive(spec.action),
            spec.fill?:ui.buttonColor,
            spec.outline?:if(spec.slot=="top")ui.topButtonOutlineColor else null)
        return target
    }
    fun render(ui:UiLayout,animations:Map<String,UiAnimationSpec>) {
        layout=ui
        topHost.removeAllViews()
        bottomHost.removeAllViews()
        buttons.clear()
        val top=ui.buttons.filter{it.slot=="top"}
        val bottom=ui.buttons.filter{it.slot=="bottom"}
        val availablePx=widthSource.width.takeIf{it>0}?:activity.resources.displayMetrics.widthPixels
        val totalDp=availablePx/activity.resources.displayMetrics.density
        val windowDp=totalDp.toInt()
        val upperBound=if(top.isEmpty())46f else ((totalDp-186f)/top.size)-4f
        val size=ui.adaptive.touchSizeDp(ui.topButtonSizeDp,windowDp).toFloat()
            .coerceAtMost(upperBound.coerceAtLeast(28f))
        for(spec in top) {
            val view=button(spec,ui,animations)
            topHost.addView(view,LinearLayout.LayoutParams(dp(size),dp(size)).apply {
                leftMargin=dp(4f)
            })
            buttons[spec.id]=view
        }
        for(group in bottom.chunked(OrientationLayoutManager.bottomColumns(
            activity.resources.configuration,bottom.size))) {
            val row=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
            group.forEachIndexed { index,spec ->
                val view=button(spec,ui,animations)
                row.addView(view,LinearLayout.LayoutParams(0,
                    dp(ui.adaptive.touchSizeDp(spec.heightDp,windowDp).toFloat()),1f).apply {
                    if(index>0)leftMargin=dp(8f)
                })
                buttons[spec.id]=view
            }
            bottomHost.addView(row,LinearLayout.LayoutParams(-1,-2).apply {
                topMargin=dp(8f)
            })
        }
    }
}
