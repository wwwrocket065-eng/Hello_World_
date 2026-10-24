# GlassStudy 0.2.62 — light indicator smoothness verification

Date: 2026-10-22

## Cause and fix
The previously shipped 0.2.61 AZIMUTH/ELEVATION guide lines used a Boolean `nearSun` hit test, dropping a whole 2° sampled segment whenever any part was near the sun. This caused the visible ends of the arc to jump rather than continuously slide under the sun marker. In addition, `hide()` set `mode=null` before the alpha fade finished, making the arc and degree caption disappear immediately; a fast re-show forced `alpha=0f` and flashed.

In `0.2.62` a reusable, allocation-conscious `GuideGapClipMath` clips each line segment against the circular glyph gap analytically, exposing the visible pieces with continuously varying endpoints. Both rotation and elevation guides use it. The existing View alpha animator is fixed to retain the drawn mode until fully faded out and reverse interrupted fades from their current opacity. Existing signed `.pupdate` values `fadeInMs`, `fadeOutMs` and `guideGapUnits` remain authoritative. No visual changes were made to the circular sun glyph or its brightness/glow.

## Builds and checks
- Source SHA: `95587ed1ea22e093b1a18d207f15fce1416fffef`, branch `glassstudy-filament-ci`.
- Three SUCCESS GitHub Actions: `39749881983`, `39749889402`, `39749882358`.
- Kotlin regression compiled and executed locally via `kotlinc`: PASS; checked 141 moving-sun sample positions along with exact tangent/interior/exterior/diagonal cases. This test was run separately, *not* by the CI workflow.
- Unsigned GitHub artifact `12412852526`; final signed `GlassStudy_3D_LAB_v0.2.62_ORIGINAL_KEY.apk`.
- APK size `11144747` bytes; SHA256 `ed265173eb27f67d6afcad3b5babbe84d82df18a58eed16d45edc9c583f479ea`.
- Official Android `apksigner.jar verify`: `Verifies`, v2=true; signing cert fingerprint SHA256 `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc`, identical to 0.2.61.
- 98 unsigned/signed ZIP payload entries byte-equal and CRC clean; all 3 Filament JNI libs aligned to 16 KiB. Package and version are checked by CI unsigned APK preflight.
- No on-phone execution has been performed for this APK. Install over v0.2.61 without uninstalling; verify arcs, labels, interrupted fades, signed `20041.pupdate` and settings persist.

## Limitation
This changes the presentation only. Solar directional geometry and physical atmosphere/vacuum lighting remain as in 0.2.61. It does not claim physically accurate spectral scattering, prefiltered specular environmental reflections or glass caustics.
