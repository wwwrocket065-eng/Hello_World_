# GlassStudy v0.2.14
- Native intent handler: ACTION_VIEW (content/file) and ACTION_SEND from Android Downloads/Files.
- Local .pupdate uses the existing exact RSA signature and per-entry SHA-256 verification, bounded size, staging and rollback.
- Same activation path for local patch and background online patch; local file wins when app launched from Downloads.
- v0.2.14 diagnostics include update check status/error; signed online patches unchanged.
- No APK installer permission; original applicationId and original signing key required for upgrade.
- Warning: Android file managers vary in MIME type for unknown extensions; opening from Downloads should offer GlassStudy for common generic/text MIME. Share-to-GlassStudy is fallback; on-device UI remains to be tested.