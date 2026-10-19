package com.glassstudy.filament

import kotlin.math.abs
import kotlin.math.hypot

/**
 * A single input-only axis arbiter for physical lighting gestures.
 * Direction is locked after the initial movement threshold until pointer
 * release or a transition to a two-finger pinch. It never draws or changes
 * the render engine, so passive indicator views can observe its Axis.
 */
class LightGestureController(private val lockThresholdDp: Double = 6.0) {
    enum class Axis { AZIMUTH, ELEVATION }
    data class Motion(val axis: Axis, val deltaDp: Double)

    private var lockedAxis: Axis? = null
    private var waitingX = 0.0
    private var waitingY = 0.0

    fun reset() {
        lockedAxis = null
        waitingX = 0.0
        waitingY = 0.0
    }

    fun move(dxDp: Double, dyDp: Double): Motion? {
        if (!dxDp.isFinite() || !dyDp.isFinite()) return null
        if (lockedAxis == null) {
            waitingX += dxDp
            waitingY += dyDp
            if (hypot(waitingX, waitingY) < lockThresholdDp) return null
            lockedAxis = if (abs(waitingY) >= abs(waitingX)) Axis.ELEVATION else Axis.AZIMUTH
            val delta = if (lockedAxis == Axis.ELEVATION) waitingY else waitingX
            waitingX = 0.0
            waitingY = 0.0
            return Motion(lockedAxis!!, delta)
        }
        return Motion(lockedAxis!!, if (lockedAxis == Axis.ELEVATION) dyDp else dxDp)
    }
}
