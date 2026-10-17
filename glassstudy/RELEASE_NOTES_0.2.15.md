# GlassStudy v0.2.15 — honest updater diagnostics

Fixed: v0.2.14 left "verified; awaiting activation" in diagnostics after the patch had already been installed. Status now updates at staging, activation, successful Filament frame confirmation, rejection, download error and crash rollback.

Diagnostic now shows current installed patch, last frame-confirmed patch, server patch and current result, separately. A patch is confirmed only after the renderer completes its post-install frame check; previously installed v20017 remains the baseline.

No sideload APK permission was added. Local signed .pupdate opening from Downloads remains supported; internet download at startup continues, with signature, hashes, staging and rollback. Application ID and APK signing identity are unchanged.

Planned test: publish signed UI-only .pupdate v20018 after signing this APK. Verify it changes the button label and diagnostics reports installed=20018, confirmed=20018. If the patched model still looks wrong, request a scene screenshot separately; a good patch status does not prove good geometry.