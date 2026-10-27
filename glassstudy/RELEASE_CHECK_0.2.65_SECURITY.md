# GlassStudy 0.2.65 — security hardening after Google Play Protect PHA warning

Date: 2026-10-25. Original warning: user screenshot of v0.2.64 installation: Play Protect «Вредоносное приложение заблокировано» / potentially harmful application. **Do not dismiss this as simply an unknown source warning or tell the user to install anyway.** Google classification and exact cause are unknown.

## Minimal-privilege fix

The Android manifest of v0.2.64 declared MANAGE_EXTERNAL_STORAGE (All files access) solely to automatically inspect shared Downloads for signed .pupdate packages. It is a high-risk sensitive permission under official Android / Google Play policy, even when user-approved. This was an unnecessary privilege for the 3D editor. **We have no proof it caused the PHA classification.**

v0.2.65 completely removes MANAGE_EXTERNAL_STORAGE from the merged binary manifest and eliminates all broad-storage request code. The local signed patch updater now uses Android's Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE), with a one-time user-selected Downloads/GlassStudy_Patches folder and a persistable read grant. It scans this folder automatically on subsequent launches/resumes, keeping signed SHA256/RSA validation, rollback, offline/online patch support. Previous SAF grants remain valid. Due to Android 11+ restrictions, the root Downloads folder cannot be granted via the picker, so this security improvement requires the subfolder for local patches. Unnecessary text/html VIEW and wildcard SEND intent filters were removed.

## Build and APK validation

- Source SHA 143b9bb78d0972e76f5c3ad7f48e9b1f7aa8008e.
- GitHub Actions: Android Build 40155752964 and 40155755522, Filament Android 40155752948 — all SUCCESS. CI source/static test, published RSA patch tamper tests, fast signed-patch test, Android native compile, APK preflight succeeded.
- Unsigned CI artifact 12584163827, final output GlassStudy_3D_LAB_v0.2.65_ORIGINAL_KEY.apk.
- Signed APK size 11590813 bytes, SHA-256 `3407907366c4f82e72cc82526a639d664e81a795d85ea5240b567b927ff35b79`.
- Official Android apksigner.jar --verbose --print-certs: Verifies, v2=true; original signing certificate SHA256 `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc`, exactly matches independently verified v0.2.64. APK signed locally using owner's off-GitHub original key backup; private files deleted by temporary directory cleanup.
- Verified merged **binary** AndroidManifest.xml in prior v0.2.64 included MANAGE_EXTERNAL_STORAGE but final v0.2.65 does not. All 40 ZIP payload entries bit-for-bit identical to unsigned CI artifact; CRC clean; native JNI libraries 16 KiB aligned. Package com.glassstudy.filament.stable, version 0.2.65 (20065).
- The one goblet, four LOD meshes Low/Medium/High/Ultra, thin grid no fill, original signed patch verification and physical lighting remain unchanged.

## Still unverified

No actual phone installation/Play Protect scan has been performed on v0.2.65. **Do not claim Play Protect warning resolved** solely because broad privilege was removed. This is not a malware scan, and signed APK integrity does not prove lack of malicious code or a false positive. If Google still flags the new APK, pursue Google's Play Protect classification appeal with exact package, cert and artifact SHA256; do not disable Play Protect or change signing identity as a workaround.

References:
- https://developers.google.com/android/play-protect/warning-dev-guidance
- https://support.google.com/googleplay/android-developer/answer/10467955
