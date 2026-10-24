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
            val ui = original.copy(buttons = original.buttons.map { button ->
                if (button.slot == "top" && button.action == "resetCamera")
                    button.copy(action = "openScene", icon = "scene", label = "")
                else if (button.slot == "top" && button.action == "menu")
                    button.copy(icon = "gear", label = "")
                else button
            })
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
