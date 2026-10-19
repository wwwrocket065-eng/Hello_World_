package com.glassstudy.filament

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Generic safe native renderer for signed patch-defined windows.
 *
 * Rendering code lives here rather than in MainActivity. Patches only supply
 * validated data; the Activity explicitly implements each allowed binding.
 * A patch can add/remove/reorder labels, headings, buttons, toggles and sliders
 * without a new APK, but cannot create or invoke arbitrary classes or actions.
 */
object PatchWindowRenderer {
    private fun dp(activity: Activity, value: Float): Int =
        (activity.resources.displayMetrics.density * value + 0.5f).toInt()

    private fun background(color: Int, outline: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            setStroke(1, outline)
        }

    fun show(
        activity: Activity,
        window: DeclarativeWindow,
        animation: UiAnimationSpec?,
        readBinding: (String) -> Float,
        writeBinding: (String, Float) -> Unit,
        performAction: (String) -> Unit
    ): AlertDialog {
        val theme = GlassDialogFactory.theme
        val orientation = activity.resources.configuration.orientation
        val fraction = (if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            window.landscapeWidthFraction ?: window.widthFraction else window.widthFraction)
            ?: theme.widthFraction

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val inset = dp(activity, theme.paddingDp.toFloat())
            setPadding(inset, inset, inset, inset)
            background = background(theme.background, theme.border,
                dp(activity, theme.cornerDp).toFloat())
        }
        panel.addView(TextView(activity).apply {
            text = window.title
            textSize = theme.titleTextSizeSp
            setTextColor(theme.accent)
            setPadding(0, 0, 0, dp(activity, theme.rowSpacingDp.toFloat()))
        })

        var dialog: AlertDialog? = null
        val elements = window.elements
        elements.forEach { item ->
            val margin = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(activity, theme.rowSpacingDp.toFloat())
            }
            when (item.type) {
                "heading", "text" -> {
                    val label = TextView(activity).apply {
                        text = item.label
                        textSize = if (item.type == "heading") theme.titleTextSizeSp else
                            theme.controlTextSizeSp
                        setTextColor(if (item.type == "heading") theme.accent else theme.text)
                        setPadding(dp(activity, 4f), dp(activity, 5f),
                            dp(activity, 4f), dp(activity, 5f))
                    }
                    panel.addView(label, margin)
                }
                "button" -> {
                    val button = TextView(activity).apply {
                        text = item.label
                        textSize = theme.controlTextSizeSp
                        setTextColor(theme.text)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(activity, theme.controlHorizontalPaddingDp.toFloat()),
                            0, dp(activity, theme.controlHorizontalPaddingDp.toFloat()), 0)
                        background = background(theme.control, theme.border,
                            dp(activity, theme.controlCornerDp).toFloat())
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            dialog?.dismiss()
                            item.action?.let(performAction)
                        }
                    }
                    margin.height = dp(activity, theme.rowHeightDp.toFloat())
                    panel.addView(button, margin)
                }
                "switch" -> {
                    val binding = requireNotNull(item.binding)
                    val toggle = Switch(activity).apply {
                        text = item.label
                        textSize = theme.controlTextSizeSp
                        setTextColor(theme.text)
                        setPadding(dp(activity, theme.controlHorizontalPaddingDp.toFloat()), 0,
                            dp(activity, theme.controlHorizontalPaddingDp.toFloat()), 0)
                        isChecked = readBinding(binding) >= 0.5f
                        setOnCheckedChangeListener { _: CompoundButton, on: Boolean ->
                            writeBinding(binding, if (on) 1f else 0f)
                        }
                        background = background(theme.control, theme.border,
                            dp(activity, theme.controlCornerDp).toFloat())
                    }
                    margin.height = dp(activity, theme.rowHeightDp.toFloat())
                    panel.addView(toggle, margin)
                }
                "slider" -> {
                    val binding = requireNotNull(item.binding)
                    val range = item.maximum - item.minimum
                    val steps = ceil(range / item.step).toInt().coerceIn(1, 1000)
                    val group = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(activity, 7f), dp(activity, 8f),
                            dp(activity, 7f), dp(activity, 6f))
                        background = background(theme.control, theme.border,
                            dp(activity, theme.controlCornerDp).toFloat())
                    }
                    val title = TextView(activity).apply {
                        setTextColor(theme.text)
                        textSize = theme.controlTextSizeSp
                    }
                    val initial = readBinding(binding).coerceIn(item.minimum, item.maximum)
                    fun displayed(value: Float): String =
                        item.label + ": " + value.roundToInt().toString()
                    title.text = displayed(initial)
                    group.addView(title)
                    group.addView(SeekBar(activity).apply {
                        max = steps
                        progress = ((initial - item.minimum) / item.step)
                            .roundToInt().coerceIn(0, steps)
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                                val value = (item.minimum + progress * item.step)
                                    .coerceAtMost(item.maximum)
                                title.text = displayed(value)
                                if (fromUser) writeBinding(binding, value)
                            }
                            override fun onStartTrackingTouch(bar: SeekBar?) {}
                            override fun onStopTrackingTouch(bar: SeekBar?) {}
                        })
                    })
                    panel.addView(group, margin)
                }
            }
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(panel, ViewGroup.LayoutParams(-1, -2))
        }
        dialog = GlassDialogFactory.show(activity, scroll, fraction, key = "menu")
        val shown = dialog
        if (animation != null) panel.post {
            if (shown.isShowing) UiAnimationEngine.enter(panel, animation,
                activity.resources.displayMetrics.density)
        }
        return shown
    }
}
