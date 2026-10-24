package com.glassstudy.filament

import android.content.res.Configuration

/**
 * Reusable orientation-aware layout policy. It does not own an Activity or the
 * Filament renderer; only translates screen configuration into UI layout rules.
 */
object OrientationLayoutManager {
    fun dialogWidthPx(configuration: Configuration, availableWidthPx: Int, fraction: Float): Int {
        require(fraction in 0.6f..1f)
        // Honor the exact signed patch setting in both orientations; never
        // silently cap landscape overrides (e.g. 0.95) to a hardcoded value.
        return (availableWidthPx * fraction).toInt().coerceIn(1, availableWidthPx.coerceAtLeast(1))
    }

    fun bottomColumns(configuration: Configuration, visibleButtons: Int): Int {
        if (visibleButtons <= 1) return 1
        return if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            visibleButtons.coerceAtMost(4) else 2
    }
}
