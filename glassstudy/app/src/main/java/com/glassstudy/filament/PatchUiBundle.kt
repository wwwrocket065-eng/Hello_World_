package com.glassstudy.filament

/**
 * Immutable, fully validated snapshot of every patch-controlled UI/scene module.
 * Construct all modules before changing the live Activity state. Failed parsing
 * leaves the currently active configuration untouched.
 */
data class PatchUiBundle(
    val ui: UiLayout,
    val camera: CameraModuleConfig,
    val render: RenderModuleConfig,
    val diagnostics: DiagnosticsModuleConfig,
    val materials: MaterialModuleConfig,
    val scene: SceneModuleConfig,
    val windows: WindowStyleConfig,
    val overlay: OverlayModuleConfig
) {
    companion object {
        fun read(updater: PatchManager, candidate: PatchManager.Candidate? = null): PatchUiBundle {
            val original = UiLayout.parse(updater.readUiConfig(candidate))
            // Compatibility for previously signed patches whose toolbar button
            // was the obsolete reset arrow.
            val migratedButtons = original.buttons.map { button ->
                if (button.slot == "top" && button.action == "resetCamera")
                    button.copy(action = "openScene", icon = "scene", label = "")
                else if (button.slot == "top" && button.action == "menu")
                    button.copy(icon = "gear", label = "")
                else button
            }
            // Existing signed 20043 UI resources have three bottom buttons.
            // Add the native quality panel only to that exact legacy layout,
            // leaving the updater, signatures, and arbitrary patch layouts alone.
            val legacyDock=migratedButtons.filter { it.slot=="bottom" }.map { it.action }
            val controls=if(legacyDock==listOf("toggleGrid","toggleFxaa","toggleAdaptivePanel")) {
                val index=migratedButtons.indexOfFirst { it.action=="toggleAdaptivePanel" }
                migratedButtons.toMutableList().apply {
                    add(index,UiButtonSpec("quality","toggleQualityPanel","bottom",
                        "quality","",46,null,null,null,false,48,32))
                }
            } else migratedButtons
            val ui=original.copy(buttons=controls)
            return PatchUiBundle(
                ui = ui,
                camera = CameraModuleConfig.parse(updater.readModuleConfig("camera", candidate)),
                render = RenderModuleConfig.parse(updater.readModuleConfig("render", candidate)),
                diagnostics = DiagnosticsModuleConfig.parse(updater.readModuleConfig("diagnostics", candidate)),
                materials = MaterialModuleConfig.parse(updater.readModuleConfig("materials", candidate)),
                scene = SceneModuleConfig.parse(updater.readModuleConfig("scene", candidate)),
                windows = WindowStyleConfig.parse(updater.readModuleConfig("windows", candidate)),
                overlay = OverlayModuleConfig.parse(updater.readModuleConfig("overlay", candidate))
            )
        }
    }
}
