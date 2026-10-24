# Animation engine — patch example and verification

Source feature (not guaranteed installed until a signed APK is built and tested).
To test animations via a *signed* `.pupdate`, add the optional property to the existing `ui/layout.json` document, leaving all original required keys unchanged:

```json
"animations": {
  "diagnostics": {
    "durationMs": 180,
    "alphaFrom": 0.7,
    "scaleFrom": 0.9,
    "rotationFrom": 0,
    "translateYDp": 4
  },
  "menu": {
    "durationMs": 180,
    "alphaFrom": 1,
    "scaleFrom": 0.93,
    "rotationFrom": 0,
    "translateYDp": 0
  }
}
```

IDs match `uiButtons[].id`, not the action. Clicking a targeted button animates that View while invoking its existing action. Defaults leave unconfigured controls unchanged.
Limits: duration 0..2000 ms, opacity 0..1, scale 0.5..1.5, rotation -360..360 degrees, translateY -120..120 dp. Unknown fields or invalid numbers reject the candidate UI patch.

## Regression checklist
- Older signed `glassstudy-ui-v1` layouts with no `animations` continue to parse and show the same visual behavior.
- Valid signed patch changes only targeted buttons.
- Invalid animation values reject activation; installed resources remain unaffected.
- Multiple clicks, backgrounding and device rotation do not leave views translucent or scaled.
- Confirm native dialog and dynamic-window behavior remain functional.
- Rebuild and sign APK with original official certificate; verify signature; confirm on Android device.

Future: apply the same engine to windows and other View-based components, then light indicators and Filament scene adapter. Do not claim these consumers are already supported.
