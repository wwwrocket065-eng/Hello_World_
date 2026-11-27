# 0.7.0
Per-chat participant selection replaces global slider; old count used only for defaults during migration. Selected IDs validated natively, excluded agents never scheduled. Coordinator only if selected; first selected summarizes otherwise. Chat metadata stored alongside existing history; titles generated locally after a completed turn, manual rename protected. Chat deletion includes project files after explicit confirmation. Distinct default glyphs; existing custom avatars kept. Same package ID and persistent local signing key required for upgrades. CI output is temporary-signed. No device runtime tests.


## 0.8.0
- Unified vector avatar icons for existing default glyphs; user colors and custom glyphs retained.
- Individual prompt editor opens directly from participant list; draft selection retained on return. Settings agent editor is appearance-only. Reset appearance no longer clears prompt.
- Existing model files and chat storage unchanged. CI APK uses an ephemeral signing key and must be re-signed with the original key before distribution as an in-place update.

- Renamed Pixie to Selena (including existing old default name); redesigned Selena and Orion vector avatars. Agent IDs, prompts, custom colors and other custom names remain unchanged.

- Distinct default agent colors: violet, lunar silver, orange, blue, red, green, teal, gold, magenta, graphite. Explicit user avatar-color settings remain untouched.

## 0.8.1 — separate installation
- New permanent application ID `local.airis.agents.stable`; old installation `local.airis.agents` remains untouched. Transfer GGUF using existing SAF model backup/import. Chats and settings are not automatically migrated.
- CI uses temporary signing only; distribution APK must be signed locally with persistent Konsilium key. Never publish the key or CI-signed APK as stable.
