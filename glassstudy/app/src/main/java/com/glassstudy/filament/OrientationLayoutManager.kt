package com.glassstudy.filament

import android.content.res.Configuration

/**
 * Reusable orientation-aware layout policy. It does not own an Activity or the
 * Filament renderer; only translates screen configuration into UI layout rules.
 */
object OrientationLayoutManager {
    fun bottomColumns(configuration: Configuration, visibleButtons: Int): Int {
        if (visibleButtons <= 1) return 1
        return if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            visibleButtons.coerceAtMost(4) else 2
    }
}
