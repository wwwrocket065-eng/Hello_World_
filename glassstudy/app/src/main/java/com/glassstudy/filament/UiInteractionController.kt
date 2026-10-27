package com.glassstudy.filament

import android.os.SystemClock

/**
 * The single validated action and state-binding contract shared by toolbar
 * buttons, dynamic windows, overlays and future signed patch components.
 *
 * Signed patches declare action/binding IDs only. Their data never determines
 * Kotlin/Java class names, Android intents, preference names or executable code.
 */
object UiInteractionRegistry {
    val nativeActions: Set<String> = setOf(
        "diagnostics", "toggleLight", "resetCamera", "openScene", "menu",
        "selectModel", "selectQuality", "toggleGrid", "toggleFps",
        "toggleFxaa", "toggleAdaptivePanel", "toggleQualityPanel"
    )
    val toggleBindings: Set<String> = setOf("lightMode", "grid", "fps", "fxaa")
    val sliderBindings: Set<String> = setOf("brightnessPercent", "adaptivityPercent", "qualityLevel")
    private val windowId = Regex("^[a-z][a-zA-Z0-9_]{0,31}$")

    fun acceptsAction(action: String): Boolean =
        action in nativeActions || (action.startsWith("openWindow:") &&
            windowId.matches(action.substringAfter(':')))

    fun acceptsBinding(type: String, binding: String): Boolean = when (type) {
        "switch" -> binding in toggleBindings
        "slider" -> binding in sliderBindings
        else -> false
    }
}

/**
 * Controller owns dispatch, toggle/close behaviour and validation. The host
 * handles actual device/render effects and state storage. This keeps
 * MainActivity from having a parallel action implementation for each UI type.
 */
class UiInteractionController(private val host: Host) {
    interface Host {
        fun isDiagnosticsOpen(): Boolean
        fun diagnosticsLastDismissedAtMs(): Long
        fun closeDiagnostics()
        fun showDiagnostics()
        fun isWindowOpen(key: String): Boolean
        fun closeWindow()
        fun openPatchWindow(id: String)
        fun performNativeAction(action: String)
        fun readState(binding: String): Float
        fun writeState(binding: String, value: Float)
        fun windowMode(key: String): String
    }

    fun read(binding: String): Float {
        require(binding in UiInteractionRegistry.toggleBindings ||
            binding in UiInteractionRegistry.sliderBindings) { "Unsupported UI binding" }
        return host.readState(binding)
    }

    fun write(binding: String, value: Float) {
        require(value.isFinite()) { "UI state value is not finite" }
        require(binding in UiInteractionRegistry.toggleBindings ||
            binding in UiInteractionRegistry.sliderBindings) { "Unsupported UI binding" }
        host.writeState(binding, value)
    }

    fun dispatch(action: String) {
        require(UiInteractionRegistry.acceptsAction(action)) { "Unsupported UI action" }
        if (action.startsWith("openWindow:")) {
            host.closeDiagnostics()
            host.closeWindow()
            host.openPatchWindow(action.substringAfter(':'))
            return
        }
        if (action == "diagnostics") {
            val mode = host.windowMode("diagnostics")
            if (host.isDiagnosticsOpen()) {
                if (mode != "openOnly") host.closeDiagnostics()
            } else if (mode != "closeOnly") {
                // PopupWindow may dismiss because the click hit the toolbar. Do
                // not reopen it on that same tap (Android 16 touch behavior).
                val elapsed = SystemClock.uptimeMillis() -
                    host.diagnosticsLastDismissedAtMs()
                if (elapsed !in 0L..280L) {
                    host.closeWindow()
                    host.showDiagnostics()
                }
            }
            return
        }
        if (action == "openScene" || action == "menu") {
            val mode = host.windowMode(action)
            if (host.isWindowOpen(action)) {
                if (mode != "openOnly") host.closeWindow()
                return
            }
            if (mode == "closeOnly") return
        }

        host.closeDiagnostics()
        host.closeWindow()
        when (action) {
            "toggleLight" -> write("lightMode", if (read("lightMode") >= .5f) 0f else 1f)
            "toggleGrid" -> write("grid", if (read("grid") >= .5f) 0f else 1f)
            "toggleFps" -> write("fps", if (read("fps") >= .5f) 0f else 1f)
            "toggleFxaa" -> write("fxaa", if (read("fxaa") >= .5f) 0f else 1f)
            "toggleAdaptivePanel" -> host.performNativeAction("toggleAdaptivePanel")
            "toggleQualityPanel" -> host.performNativeAction("toggleQualityPanel")
            "resetCamera", "openScene", "menu", "selectModel", "selectQuality" ->
                host.performNativeAction(action)
            else -> throw IllegalArgumentException("Unknown native action: $action")
        }
    }
}
