# GlassStudy v0.2.12 — camera, edge-to-edge and mesh regression
- UI respects Android 15/16 system-bar insets at top and bottom.
- Portrait default camera distance increased from 4.4 to 7.4 to avoid cropped glass; inverse pinch preserved but dampened (power 0.5).
- GLB generator repaired: the _debug variants previously referenced material index 1 with only 1 material, and declared an incorrect buffer byte length. Both are now checked for all 66 models before compiling.
- Regular glass now has small solid rim/foot highlights and moderately reduced transmission so the vessel stays discernible against the neutral background.
- Same app id and original signing certificate. Auto-verified data-only .pupdate stays enabled, Android APK sideload privilege stays absent.
- Release status: static tests plus CI and external runtime check needed.