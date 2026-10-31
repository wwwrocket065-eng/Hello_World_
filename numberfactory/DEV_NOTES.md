# DEV_NOTES — Numera Android

## 2026-10-31 / v0.1.0 graphical and gameplay prototype

- Repository: `wwwrocket065-eng/Hello_World_`; branch: `numberfactory-android`; project: `numberfactory/`. The existing main branch and GlassStudy/Hello_World_ files are not edited.
- Confirmed source commits:
  - `8093a163c365f2b6adbb7d3cd7f9426a5da97925`: Canvas-based playfield, game simulation, number sources, conveyors, add/multiply machines, touch construction, pan/pinch, UI, local persistence.
  - `bdc23dc4b80a24577620e777af38c4c723060906`: GitHub Actions debug APK build and conditional public preview release.
- Appearance: original dark graphite/navy grid, moving turquoise conveyors, amber number tokens, violet adders, blue multipliers, ringed target hub, modern HUD, 6 touch tools.
- Initial production line: source 2 + source 3 -> addition -> conveyor -> hub targeting 5, 12 successful deliveries to complete level 1. Level 2 target 8; further balancing and unlocks remain to be implemented.
- Build conditions: Gradle 8.9 / AGP 8.7.3, Java 17, compileSdk 35, Android 8.0+ (minSdk 26); package `com.numera.factory` separate from other apps.
- **Verified build 2026-10-31:** GitHub Actions run `41210157744`, run 7 for commit `33907d164b561a2f91a1234d6c74e6b28261ec22` finished `success`. `:app:assembleDebug` and upload artifact steps completed successfully; artifact ID `13046201283` (`numera-v0.1.0-debug-apk`). Downloaded the ZIP and extracted `app-debug.apk`, 17,473 bytes; APK ZIP CRC check passed, `classes.dex`, `classes2.dex`, AndroidManifest.xml and resources.arsc present, APK Signature Block magic found. SHA-256 of APK: `82b40eb0da568894136958f5eb26909d963e482ed0aa0e9075149c3f678abfb8`.
- SDK preparation failed initially on legacy `tools` package; fixed by using runner's preinstalled Android SDK in commit `33907d164b561a2f91a1234d6c74e6b28261ec22`.
- **Limitations:** real-device installation and touch usability are not verified. This debug APK is **not** guaranteed to install as an update over future independent builds (debug key may change). Downloads auto-update has **not** been implemented. A stable signing key and validated updater are outstanding; don't advertise them as done.
- CI planned output: `numera-v0.1.0-debug-apk` artifact; preview release tag `numera-v0.1.0-preview` only after successful build.
- Next: install on the target phone and test touch hit areas, enable persistent secure signing for in-place updates, implement/test downloads-based auto-update, then improve placement gestures, arithmetic choices, factories/upgrades and autosave.
