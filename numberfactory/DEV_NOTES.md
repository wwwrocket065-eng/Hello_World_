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

## 2026-10-31 / v0.2.0 prototype upgrade checkpoint

- Isolated branch `numberfactory-android` only; main and GlassStudy untouched.
- Commits `7325525`, `d4042d7`, `e33c342`, `ff1b301`, `5721632`, `7368065`.
- Source now starts in Camera tool (one finger pan; two-finger pinch), supports zoom down to 0.12x, immersive Android full-screen navigation.
- Fresh world uses an empty 6x6 hub and visible 3x3 numeric deposits. Miners may only be placed at discovered deposit centres; their output reaches conveyor tiles outside the 3x3 visual region. New progress uses independent v2 preferences, so previously generated demo world is not auto-imported.
- Belts: draw mode (freehand), route draft mode (multiple drag legs, finger release leaves preview intact), Confirm / Cancel, edge auto-scroll. Turns draw as bent paths. Selection/copy/paste prototype is added, with known limitations (clipboard selection and constrained drag selection).
- First 20 level goal numbers/counts updated from public Beltmatic level guide. Not all tech unlocks/mechanics implemented.
- Four GLOBAL (not per-building) upgrade categories added: belt speed, extractor throughput (up to four cardinal outputs in this 2D prototype), adder speed, multiplier speed. First eight tier requirement examples each are encoded, with per-category delivered-number counters, costs subtracted on purchase, a HUD upgrade panel, persistent upgrade levels/counters. Note: exact number IDs may vary by world in original Beltmatic. Higher tier progression and subtract/divide/exponentiation tracks not implemented.
- Operators now honor approximate cycle times based on their upgrade tiers. Belt movement rate tied to global belt upgrade. Extraction has four possible output directions (original uses twelve), so not exact parity yet.
- Release packaging uses v0.2.0 preview tag. CI build and real-device usability need separate verification; an APK cannot be claimed installed or able to update over 0.1.0 based solely on CI compilation.
- **Outstanding**: no cross/bridge/priorities implementation, no full original tech tree (all 30+ stages), no unlimited upgrade paths, no robust selection UI/undo, no advanced logistics, no autosave of view/camera, no Downloads-based auto-updater, no stable signing key. Current debug APK may require uninstall of older debug signed version if certificate changes.
