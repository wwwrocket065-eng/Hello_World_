package com.glassstudy.filament

/** Run using kotlinc LightTouchSession.kt LightTouchSessionRegression.kt -include-runtime -d test.jar */
fun main() {
    val t=LightTouchSession(tapSlopDp=6.0)
    t.begin(100f,100f)
    check(t.finish()) { "Stationary single tap must hide" }

    t.begin(100f,100f)
    t.onMove(103f,102f,2f)
    check(t.finish()) { "Minor finger jitter must still count as tap" }

    t.begin(100f,100f)
    t.onMove(119f,100f,2f)
    check(!t.finish()) { "Single-finger gesture must leave indicator visible" }

    t.begin(100f,100f)
    t.onMultiplePointers()
    check(t.hadMultiplePointers)
    t.onMove(100f,100f,2f)
    check(!t.finish()) { "Lifting fingers after a pinch is not a tap" }

    t.begin(100f,100f)
    t.cancel()
    check(!t.finish()) { "System cancellation cannot dismiss" }

    t.begin(100f,100f)
    t.onMultiplePointers()
    t.finish()
    t.begin(100f,100f)
    check(!t.hadMultiplePointers && t.finish()) {
        "New tap after a complete multi-touch release must work"
    }
    println("PASS: persistent lighting indicator tap classifier")
}
