package com.glassstudy.filament

import kotlin.math.hypot

/**
 * Pure input state machine for passive light HUD dismissal.
 * Only a new stationary SINGLE-finger tap dismisses an indicator.
 * After a pinch begins, the remaining finger may not switch the selected
 * indicator until the next completely new ACTION_DOWN.
 */
class LightTouchSession(var tapSlopDp:Double=6.0) {
    private var active=false
    private var downX=0f
    private var downY=0f
    private var stillTap=false
    var hadMultiplePointers=false
        private set

    fun begin(x:Float,y:Float) {
        active=true
        downX=x;downY=y
        stillTap=x.isFinite() && y.isFinite()
        hadMultiplePointers=false
    }

    fun onMultiplePointers() {
        if(!active)return
        hadMultiplePointers=true
        stillTap=false
    }

    fun onMove(x:Float,y:Float,density:Float) {
        if(!active || !stillTap)return
        if(!x.isFinite() || !y.isFinite() || !density.isFinite()) {
            stillTap=false
            return
        }
        val dp=density.coerceAtLeast(.1f).toDouble()
        val movement=hypot((x-downX).toDouble()/dp,(y-downY).toDouble()/dp)
        if(movement>tapSlopDp)stillTap=false
    }

    fun finish():Boolean {
        val isTap=active && stillTap && !hadMultiplePointers
        cancel()
        return isTap
    }

    fun cancel() {
        active=false
        stillTap=false
        hadMultiplePointers=false
    }
}
