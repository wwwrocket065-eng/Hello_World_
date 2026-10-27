# GlassStudy 0.2.60 — signed release check (2026-10-22)

## Fix
- In v0.2.59 the azimuth ellipse sampled actual sun elevation, making all guide points shrink by cos(elevation), collapse at zenith, and mirror after zenith.
- In v0.2.60 the azimuth ring uses `LightGuideProjection.azimuthOrbit(...)`, a fixed zero-elevation world-horizontal ring. Guide radius and projected height remain those of the original elevation 0° reference for the same camera yaw/pitch. Camera-dependent perspective/orthographic projection remains unchanged.
- The projected sun glyph still uses `LightGuideProjection.azimuth(...,lightElevation)` and therefore follows the real `LightDirectionMath.source` 3D vector. It may move off the fixed reference track when above the horizon; this is physically intentional.
- ELEVATION semicircle, glyph ring/rays, glow and brightness 0% visuals remain untouched.

## Build and signature
- Source: `b3d7f246e899a8e96b52c2072e8fa0fd722764d3`, branch `glassstudy-filament-ci`.
- Android Build CI `39716014858`, `39716018985` and Filament Android `39716014920`: all SUCCESS; project tests, patch integrity, fast patch tests, native Gradle compile and APK preflight passed.
- GitHub unsigned artifact `12398209059`; final locally signed APK `GlassStudy_3D_LAB_v0.2.60_ORIGINAL_KEY.apk`.
- APK SHA-256 `9320d699157e44866d6b3066334d8e43788e9de4d6997530a7703a05c5bbbb8f`, size `11128363` bytes.
- Official Android apksigner verification: `Verifies`; Scheme v2 `true`, v1/v3 `false`; single 3072-bit RSA signer. Cert SHA256 `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc` matches prior release 0.2.59. Private key came from the owner's Library backup and was not committed/uploaded to GitHub.
- 98/98 payload ZIP entries identical signed/unsigned; CRC pass; native libs 16 KiB aligned; `resources.arsc` aligned. AndroidManifest `com.glassstudy.filament.stable`, `versionName=0.2.60`, `versionCode=20060` (previous 20059), `minSdk=26`.
- Original signed patches and user preferences remain in Android app data when the APK is upgraded in place without uninstalling, but this continuity is not yet phone-tested.

## Geometric regression (yaw=0, pitch=30°, radius=70)

| Sun elevation | Fixed ring point X for azimuth 90° | Real sun glyph X | Horizontal light-ray / shadow X |
|---|---:|---:|---:|
| 0° | +70.000 | +70.000 | −1.000 |
| 45° | +70.000 | +49.497 | −0.707 |
| 90° | +70.000 | 0.000 | 0 |
| 135° | +70.000 | −49.497 | +0.707 |
| 180° | +70.000 | −70.000 | +1.000 |

The actual guide X/Y bounds for the whole orbit are unchanged with sun elevation, but change with camera angle as expected. Verified mathematically offline. On-device rendering is pending.

## Android acceptance still needed
Install this signed APK **OVER 0.2.59**. Do not uninstall. Inspect circle size at 0,45,90,135,180°, true sun marker vs actual shadow, original patch 20041 and settings retention. This release fixes an indicator rendering issue; it does not introduce a physically correct multi-scattering atmosphere, specular environment cubemaps, transparent shadows, or caustics.
