# GlassStudy theme engine — implementation contract

## Current status (2026-10-19)
Source-level first stage integrated with `UiLayout.parse` through `UiThemeEngine`.
Not yet a complete app-wide theme: dialogs (`WindowStyleConfig`), overlay (`OverlayModuleConfig`), scene/materials and independently drawn icons still need consumers. Build/device verification is pending.

## Supported safe signed-patch layout fragment
Place the following optional object into existing signed `ui/layout.json`:
```json
"theme": {
  "palette": {
    "accent": "#9EDCE1",
    "surface": "#20313D"
  },
  "colors": {
    "buttonTextColor": "@accent",
    "topColor": "@surface"
  },
  "buttons": {
    "diagnostics": {
      "textColor": "@accent"
    }
  }
}
```

Allowed global targets: topColor, bottomColor, buttonColor, buttonTextColor, textColor, systemBarColor, logoColor, topButtonOutlineColor, selectorTextColor, fpsTextColor. Button targets: fill, outline, textColor. Values are #RRGGBB or @token. Unknown keys and tokens reject the patch. Legacy patches without theme retain existing behavior. Explicit per-button theme overrides have precedence over legacy per-button colors. No patch may execute arbitrary Kotlin.

## Next implementation stages
1. Make all UI consumers accept the same immutable theme snapshot; unify dialogs, overlays, captions, icon tint and light indicators using stable component IDs.
2. Create separate validated animation and interaction engines, with predefined effects/actions, lifecycle and input boundaries.
3. Support patchable layout trees with stable IDs and accessibility bounds; validate and stage changes, with rollback on failed frame.
4. Add CI/unit tests for legacy compatibility, invalid tokens, targeted overrides, reapplication and rollback; compile APK and test on Android before release.
5. Only after interface and models are finished: hour-long performance profiler.

Never label an individual module as proof that the entire app is modular.
