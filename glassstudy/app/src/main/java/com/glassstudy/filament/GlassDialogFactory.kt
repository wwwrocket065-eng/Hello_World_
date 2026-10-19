package com.glassstudy.filament

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup

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
}
