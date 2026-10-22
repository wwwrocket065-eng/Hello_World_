# GlassStudy v0.2.61 — signed APK release audit (2026-10-22)

**User-visible fix:** the azimuth SUN MARKER now stays on the fixed azimuth circle; the whole orbit keeps its 0° elevation radius/height as the physical sun rises. An independent 3D sun projection was accidentally used for the icon in 0.2.60; this is corrected.

**Physical accuracy:** the actual Filament sun/shadow remains directed by `LightDirectionMath.incoming`. The AZIMUTH marker uses a normalized horizontal *bearing* via `LightDirectionMath.azimuthMarkerOnHorizon`, projecting onto exactly the same reference ellipse as the track. Sun elevation >90° reverses side. At exact zenith, horizontal direction is undefined, and the UI holds the approaching side; expect a 180° change in ring marker side immediately after crossing 90°. A fixed orbit with strict shadow-side accuracy cannot simultaneously avoid this singularity.

**Source:** GitHub branch `glassstudy-filament-ci`, source commit `0396c7efabd05d2275d4667dd0fc19abc6b96be6`.

**CI:** Android Build runs `39717219814`, `39717224011` and Filament Android `39717219807` all **SUCCESS**.

**Tests:** GitHub tests (source, patch integrity, fast patch, native Android build, preflight alignment) passed; offline 315-case bearing/ring/shadow math test passed.

**Signed APK:** `GlassStudy_3D_LAB_v0.2.61_ORIGINAL_KEY.apk`, 11,128,363 bytes, SHA-256 `1003b8d39d9f1df76914d7a547294655b43680b878f6a2c3314b24d41e461a97`.

**Cert/signing:** original key only (no new key). Official Android apksigner `verify --verbose --print-certs` passed v2, cert SHA-256 `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc` matches signed v0.2.60. Binary AndroidManifest stable `com.glassstudy.filament.stable`, code `20061` vs old `20060`; all 98 APK files equal unsigned CI artifact `12398993586`; 3 JNI libs aligned to 16 KiB.

**Compatibility:** install OVER the existing APK; do not uninstall. The signed `20041.pupdate` and user settings are not changed by the patch-independent Kotlin-only fix. Real installation, visual shadow checks and preference preservation on user's Android device are pending, not claimed as verified.

**Known rendering limits:** the previously documented sky lighting remains an analytical SH approximation; this UI correction does not solve glass specular reflections, transmission caustics or full spectral atmosphere.
