# GlassStudy 3D LAB — Play Protect review evidence for 0.2.69

Date: 2026-10-25
Source commit (built): `fb30597cb5a08fedb75f6228847d5346f413a4a3`
Android package: `com.glassstudy.filament.stable`
Target SDK: 35; minimum SDK: 26; ABI: arm64-v8a
**Status: Google Play Protect blocked installation on user's Android 16 device. Do not override the block, claim a false positive, or distribute this version as an accepted working update.**

## User-facing evidence
- Screenshot at ~10:17: Google Play Защита says **«Вредоносное приложение заблокировано»** and warns of possible device/data harm. This is a harmful-app classification, not merely an "unknown source" prompt.
- Screenshot at ~10:22: Android package installer says **«Приложение не установлено»**. This generic dialog alone does not reveal the `PackageInstaller` status/error code. There is no evidence that 0.2.69 was installed successfully. Preserve existing application and data.

## Exact APK and verification
- File: `GlassStudy_3D_LAB_v0.2.69_ORIGINAL_KEY.apk`
- SHA-256: `e1b6fb8bd7e39a8f9597f02ccc29c6d358df1590908bcdd0ef965e070b263193`
- Size: 8,854,130 bytes; 33 ZIP entries; ZIP CRC test passes.
- Local check using **official Android apksigner.jar**: `verify --verbose --print-certs --min-sdk-version 26` returns `Verifies`, APK Signature Scheme v2 true, v3 true, one signer.
- X.509 APK signer SHA-256: `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc`; identical to prior 0.2.68. No rotation of APK signing identity.
- Independently recalculated three JNI library data offsets from local ZIP file headers: each aligned to 16,384 bytes. `resources.arsc` is 4-byte aligned.
- 0.2.68 vs 0.2.69 archive comparison: exactly two payload members changed, `AndroidManifest.xml` and `classes.dex`. All 31 remaining ZIP entries are byte-identical, including all 3 native Filament libraries, model assets, resources and dependencies. This isolates release changes; it does **not** establish absence of malware or a Play Protect verdict.
- Older 0.2.68 SHA-256: `f63da08e0de514daee5cd7fa520fbbe4edd39b5652205bbeb0b700baa6d365f3`.

## Permission / source-change review
- Compared repository refs `8d497d6482c36550a32cc109602b7971b308ea6d` (0.2.68) and `fb30597cb5a08fedb75f6228847d5346f413a4a3` (0.2.69): app changes are AndroidManifest, LocalPatchDirectory, MainActivity and version properties; test/docs updated.
- Manifest from the **actual binaries**: 0.2.68 declares `android.permission.INTERNET`; 0.2.69 additionally declares `android.permission.MANAGE_EXTERNAL_STORAGE`. No SMS, accessibility, contact, notification-listener, all-package-query, or installer privilege has been observed in the declared app manifest.
- `LocalPatchDirectory` checks `Environment.isExternalStorageManager()`, opens the *Android system permission settings* once when needed, then scans public `Environment.DIRECTORY_DOWNLOADS` for filenames exactly matching `GlassStudy_Filament_(\\d{5}).pupdate`. No folder picker for the user under this mode; previously approved SAF subdirectory remains as a compatibility fallback.
- `MainActivity.checkLocalThenRemote()` passes candidate files to existing PatchManager and uses signed GitHub-feed fallback if local folder empty, inaccessible or invalid.
- `PatchManager.stage()` validates an allowlisted resource path, entry count/size, signed RSA/SHA-256 canonical manifest and every resource hash; rejects malformed/downgrade/unconfirmed/crash-blocked patches. Remote update URL is constructed using an expected file name, uses HTTPS without redirects, and independently checks the feed's SHA-256 then RSA signature.
- App Gradle build declares Google Filament 1.57.1 modules and kotlinx coroutines Android 1.9.0. These binary dependencies did not change between 0.2.68 and 0.2.69. This is a scoped static/code-difference audit, **not** a complete independent security review of every third-party dependency or dynamic behavior.
- Static DEX string checks in 0.2.69 did not find `DexClassLoader`, `PathClassLoader`, `ProcessBuilder`, `SmsManager`, `AccessibilityService`, `DevicePolicyManager`, `PackageInstaller`, `MediaProjection`, `addJavascriptInterface`, `RECEIVE_SMS` or `READ_SMS`. A negative string scan is NOT proof of no potentially harmful functionality.

## Policy classification — unresolved
- Official Google guidance distinguishes the **Harmful App Blocked** classification from a generic unknown app or the specific "App blocked to protect your device" flow. Google does **not** expose its malware category or the exact triggering signal via these screenshots.
- `MANAGE_EXTERNAL_STORAGE` is a **restricted, sensitive permission** subject to Google's all-files access policy, which generally reserves it for documented qualifying core functions. Here the declared principal purpose is a 3D visualizer, and broad file access is used for convenient local update discovery. Do NOT assert that this satisfies Google Play's qualifying-core-function rule without an explicit Google policy assessment/exception. An APK installed directly outside Play and Play Store listing approval are also distinct questions.
- It is **plausible** that the newly restored permission contributes to a new Play Protect classification, but **not proven**. Importantly, the Google description of its special sideload "App blocked to protect your device" flow lists SMS/notification-listener/accessibility permissions, none declared by this app. The screenshot shows "Harmful App Blocked" instead.
- Android generic "App not installed" dialog could follow Play Protect's blocking decision or another PackageInstaller error; diagnosis would need Android `logcat` / installer failure status, which was not available from user's screenshots.
- Do not bypass Play Protect, advise disabling it, generate a new APK signing identity, or force uninstalling existing 0.2.68/data. Do not remove or replace the user-confirmed legacy Download updater without their explicit permission.

## Official Google next action
1. Google developer guidance: https://developers.google.com/android/play-protect/warning-dev-guidance
2. Direct **File a Play Protect appeal** form linked by Google: https://support.google.com/googleplay/android-developer/contact/protectappeals
3. Restricted all-files-access policy: https://support.google.com/googleplay/android-developer/answer/10467955?hl=ru
4. Submit factual details and screenshots, identifying exact APK SHA-256, package ID, original signer certificate, app purpose, actual user-consented Download scanner and signed patch validation. Ask Google to investigate the specific classification and whether corrected security/policy behavior is necessary. Do not claim the app definitively meets policy or that Google must whitelist it. The developer must personally provide the contact details/account/form authorization. **No appeal has been submitted by this assistant.**

### Draft appeal (English, factually scoped)

Subject: Request for review of Play Protect "Harmful App Blocked" classification — GlassStudy 3D LAB 0.2.69

Hello Google Play Protect team,

Please review the Play Protect classification of our Android application GlassStudy 3D LAB, package `com.glassstudy.filament.stable`, version 0.2.69 (`versionCode 20069`). On an Android 16 device the install was blocked with the message "Harmful app blocked" and was subsequently reported as not installed.

APK SHA-256: `e1b6fb8bd7e39a8f9597f02ccc29c6d358df1590908bcdd0ef965e070b263193`
Signing certificate SHA-256: `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc`

The application is an interactive 3D glass geometry/lighting viewer using the Google Filament rendering engine. It requests INTERNET access for an HTTPS update feed and, in this version, optional Android All files access (`MANAGE_EXTERNAL_STORAGE`) so users can grant access once and allow discovery of signed `.pupdate` resource patches directly in the public Downloads folder without a repeated file/folder picker. Each candidate is validated with an allowlisted resource path, RSA signature and SHA-256 hash before activation. We understand the broad permission is sensitive and are reviewing its necessity and applicable policy restrictions; we do not claim that these checks alone establish full security compliance.

The package identity and original signing certificate match version 0.2.68. APK Signature Scheme v2/v3, APK archive integrity and native library alignment pass local verification. We have screenshots of the blocking message and unsuccessful installer dialog.

Could you review and clarify which harmful behavior or policy concern led to this classification and whether an update or a reclassification is needed? We can provide the exact APK and further evidence through an authorized channel.

Thank you.
