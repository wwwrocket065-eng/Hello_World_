# GlassStudy v0.2.21 — fully patchable UI buttons

Once v0.2.21 is installed, signed `.pupdate` files can edit **ui/layout.json**
without requiring a new APK for routine interface changes:

- `uiButtons` is a declarative JSON array. Each item creates one real Android button.
  Omit an entry (or set `visible:false`) to remove it. Change `slot` or
  reorder entries to reposition the button. The UI is rebuilt on accepted signed patches.
- Allowed actions: `diagnostics, toggleLight, resetCamera, menu, selectModel,
  selectQuality, toggleGrid, toggleFps`.
- Allowed icons: `chip, sun, reset, menu, objects, quality, grid, fps, info`.
- Button entry accepts `id` (unique), `action`, `slot` (top/bottom),
  `icon`, optional `label` (required for bottom), `heightDp` (36..72),
  `fill`, `outline`, `textColor`, `highlight`, `visible`.
  Top icons are centered real Canvas vectors in *square* buttons.
  Bottom buttons are 2 per row, centered icon+label.
- Up to 12 total, 5 top, 8 bottom; duplicate IDs, unsupported actions/icons,
  invalid colors and configs with no menu/diagnostic control are rejected.
- `panelOpacity` 0.2..1 and `showFps` boolean are patchable.
- Existing signed ui/layout.json from v20023 or earlier has no uiButtons:
  **fallback is this v0.2.21 default**: 4 square top icons
  (chip diagnostics, sun, reset, menu), 2 bottom
  (Light/Camera, Grid), model and quality selectors, no Apply/Export,
  complete cyan diamond logo with extended lower underline.

Safety: patch content still requires the existing versioned SHA256/RSA update
manager. No arbitrary Kotlin code, APK classes, URLs, user files or executable
scripts are accepted through UI JSON; new button *functions* need an APK,
but additional instances / visibility / order / text / appearance of existing
functions are patchable.

CI test checks schema and compiled Kotlin; final device verification required.
The Google Filament render, glass GLBs, camera v0.1.22, signed updates and
diagnostics measurements were not intentionally modified.
