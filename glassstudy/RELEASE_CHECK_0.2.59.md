# GlassStudy v0.2.59 — independent release verification (2026-10-22)

## Provenance and reproducibility

- GitHub repository: `wwwrocket065-eng/Hello_World_`; branch: `glassstudy-filament-ci`; project: `glassstudy/`.
- CI source commit: `9a7086fc3bcca3a5ff6fc28b06e687b8a0f904a3`.
- Verified CI runs: Android Build `39709011321` and `39709006638`, Filament Android `39709006764`, all `completed/success`, all targeting the same SHA.
- GitHub Actions artifact ID `12395818171` (original unsigned APK `app-ciUnsigned-unsigned.apk`).
- Signed file: `GlassStudy_3D_LAB_v0.2.59_ORIGINAL_KEY.apk`, size `11128363` bytes, SHA-256 `b633676dfd0396296b2e84511b3e1ddd9547131e778593fb4a5f062d87616538`.
- Signed vs unsigned APK: all 98 archive members have byte-identical uncompressed contents; ZIP CRC test clean; three `arm64-v8a` shared libraries 16 KiB aligned; `resources.arsc` stored and 4-byte aligned.

## Signing and upgrade

- Parsed APK v2 signing block, verified RSA PKCS#1 v1.5 SHA-256 signature and signed content digests (1 MiB chunk scheme).
- X.509 certificate SHA-256: `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc` — exactly the same as inspected signed 0.2.58.
- AndroidManifest binary attributes: versionCode `20059` > old `20058`, versionName `0.2.59`, package `com.glassstudy.filament.stable`, minSdk 26, targetSdk 35.
- A previous audit records official `apksigner` validation. This independent pass used cryptography and binary structural verification because the official `apksigner` binary was not installed here. Do not mistake this for a newly performed official `apksigner` run.
- The old `20041.pupdate` signature has been separately verified with the unchanged RSA public key extracted from the 0.2.59 APK. Its single resource is `modules/overlay/config.json`, with verified SHA-256. `PatchManager` uses app-private `filesDir/signed_patches` and `SharedPreferences`: Android in-place upgrades preserve them; only a real Android device test can confirm actual migration and runtime behavior.
- Install over 0.2.58; do not uninstall first. No new `.pupdate` needed solely for the physical mode switch. Never put APK or patch private keys in GitHub.

## Geometric light regression

`LightDirectionMath.source(azimuth,elevation) = (cos(elevation)*sin(azimuth), sin(elevation), cos(elevation)*cos(azimuth))`.

Filament ray direction is `-source`; the azimuth indicator projects the exact same `source` via `LightGuideProjection`. For world azimuth 90° and camera yaw 0°, world/guide X has these signs (guide radius = 70):

| Physical elevation | Sun horizontal X | Incoming ray / shadow horizontal X | Guide X |
|---:|---:|---:|---:|
| 0° | +1.000 | -1.000 | +70.000 |
| 45° | +0.707 | -0.707 | +49.497 |
| 90° | 0.000 | 0.000 | 0.000 |
| 135° | -0.707 | +0.707 | -49.497 |
| 180° | -1.000 | +1.000 | -70.000 |

At exact horizon an ideal horizontal surface receives no direct solar irradiance and finite shadow length is ill-defined. At 90° the horizontal shadow direction vanishes. This is source-code and mathematical validation, not a claim that shadows were visually observed on a phone.

## Rendering invariants and remaining work

- VACUUM: no `Scene.indirectLight` and no atmospheric attenuation; only physically directed sun (off at overall brightness 0%).
- ATMOSPHERE: direct extinction and solar color plus dynamically calculated sky spherical harmonics; no studio `studio_ibl.ktx` illumination; brightness 0% makes both direct and indirect intensity zero.
- The 2D sun icon is not projected as an ellipse; its bloom/core are not scaled by material-light brightness. Existing ray-length ornament is brightness dependent.
- Model is explicitly approximate single-scattering-inspired analytical skylight. There is *no* full spectral scattering, multiple scattering, directional image of sky, HDR prefiltered specular sky reflection, transparent shadows or refractive caustics.
- Device-only acceptance still pending: v0.2.58→0.2.59 over-install, persistent settings and patch 20041, horizon lighting, direct/IBL off at 0%, indicator vs actual rendered floor shadow at 0/45/90/135/180°, and glass reflection behavior.
