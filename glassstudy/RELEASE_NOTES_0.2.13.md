# GlassStudy v0.2.13 — signed declarative interface updates

The app now supports a strictly validated `ui/layout.json` entry in RSA-SHA256-signed `.pupdate` v2 files.
Safe changes: background/button colors, button labels, layout order of built-in controls, sizes and inverse pinch sensitivity.
No arbitrary Kotlin, Java, DEX, JavaScript or downloaded executable code is accepted. Built-in button actions cannot be replaced.
The same transactional stage/activate/crash rollback mechanism used by model GLBs is retained.
Unknown or malformed UI configs are rejected before activation. The full config stays bundled as a fallback.
Existing Android system bar insets, GLB fixes and signature identity carried forward from v0.2.12.
Native API and new action implementations still require an APK update.
