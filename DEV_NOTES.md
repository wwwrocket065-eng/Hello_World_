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

## 0.8.2
- Import opens Android document picker directly, without unsupported JS confirm. Enable WebChromeClient for remaining dialogs. Fallback ACTION_GET_CONTENT; picker errors/cancellation shown in transfer status. Avoid replacing model controls when state is unchanged. Permanent stable package and signing identity unchanged.

## 0.8.3
- Restored app label Konsilium. Stable application ID unchanged. Refined the ten default avatar glyphs for consistent visual weight and size, with simplified symbols and a clearly visible Orion belt. Palette, custom avatar settings, UI layout and model import logic unchanged. Sign distribution only with existing permanent key.

## 0.8.4
- Match approved avatar preview: sparkle, moon-star, shield-check, faceted gem, sun, music notes, original seven-star Orion, lightning, snowflake and vertical audio bars. Preserve approved colors and custom settings. App label remains Konsilium; stable package and import logic unchanged.

## 0.8.5
- Use exact upstream Lucide 0.456.0 SVG geometry for the nine preview icons plus the agreed custom Orion SVG. Preserve the current palette, stable package, label and all other behavior. Include upstream license.

## 0.8.6
- Real-time token accounting from pinned llama.cpp b12785 SSE timings and final usage. Input includes cache, output includes generated tokens; per-agent totals accumulate over tool rounds, request totals include title generation. Display generation throughput from engine; no character estimates. Existing prompt format, token budget, agent order, data and package ID unchanged. UI polls existing state every 750 ms. Real-device stream test pending.
