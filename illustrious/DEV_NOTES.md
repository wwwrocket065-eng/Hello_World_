# Local SDXL v0.1.1 — 2026-11-09

Device: Xiaomi 15T, Android 16, Dimensity 8400-Ultra, Mali-G720. Fully local generation.
Repository: wwwrocket065-eng/Hello_World_, branch illustrious-android-ci.
Pinned upstream rmatif/Local-Diffusion 184b7f92cf2f810e7d5eb4b04b190a5da829005f.
Baseline GitHub Actions run 42655632161 succeeded. Local Flutter download failure was bypassed by CI.

Changes: in-app Illustrious XL v2.0 download, app-private model directory, byte/percent progress, HTTP Range resume, streamed SHA256 validation before rename/loading, automatic model connection using CPU/Q4/Flash Attention and VAE tiling. App no longer requires broad storage access on startup. Internet permission added. Diagnostics dialog with copy action, bounded log history, generation step callbacks, elapsed stopwatch, process RSS/peak RSS, available memory, process CPU using Android proc tick assumption 100Hz, explicit unavailable GPU/temperature. Sample interval two seconds; retained telemetry every ten seconds, at most 500 events.
Official model: https://huggingface.co/OnomaAIResearch/Illustrious-XL-v2.0
SHA256 c2a1a3eaa13d4c107dc7e00c3fe830cab427aa026362740ea094745b3422a331.

Pending verification: compilation of changes, Android 16 native library compatibility and actual Illustrious generation on Xiaomi hardware. No claim of device success or generation speed. Existing upstream advanced UI remains mostly English. Downloader pauses when process dies; resume from partial file next launch. Foreground/background persistent download service not implemented. Native engine version supplied by upstream; page-size support requires device verification.
Build via .github/workflows/illustrious-android.yml. Source overrides in illustrious/overlay/lib; patch_build.py wires dependency, manifest and notes. Do not overwrite overrides with upstream. Diagnostics embedded as asset.

## 0.1.2 changes and signing recovery
198 Russian UI/status/error translations across all application pages; force dark theme, black main/card background and dark popup background; system Material controls use Russian localization. No deliberate layout/shape/size changes. Technical model/engine identifiers and raw native diagnostic logs remain technical identifiers/logs. Add smoothed MB/s and download ETA; reset speed sampling on resume.

CRITICAL: 0.1.1 signing private key was ephemeral on GitHub Actions and not retained. Baseline and v0.1.1 certificate fingerprints differ, confirming CI regenerated keys. Cannot promise an in-place update or reuse private model data without the original private key. Do NOT uninstall 0.1.1 during download. 0.1.2 uses a separate application ID com.localsdxl.mobile and installs alongside it; its app-private storage is separate, so it cannot automatically use the model downloaded in 0.1.1. A dedicated permanent key is generated privately locally, backed up privately, and used to re-sign the final APK outside CI. Never put signing keys/passwords in public repository. Next 0.1.2+ builds must be signed with this preserved key and same app ID to retain downloads.


## 0.1.3 tuning and diagnostics
Research: checkpoints/mali-acceleration-research.md. Real reusable MNN SDXL/OpenCL path found in Local Dream; needs converted SDXL graph bundle and MNN-only native integration, not implemented in this build. Do not describe this release as GPU accelerated.
Current runner: replace forced getCores()*2 with user-selectable bounded CPU thread count (2/4/6/8, default4); forward through isolate initialization into native context. Optional defaults preserve other constructors. Changes require model reload and are disabled during generation. No measured speedup claimed. Compare same512x512/seed/CFG/model and2 steps at4vs8 threads before long runs.
Status: live total elapsed timer independent of native callbacks; show next step in progress and VAE-part counter separately after denoise. Stage inference uses engine messages; raw native logs retained. Copy newest80 events first, current metrics above, avoiding clipping by long download history. Set immersiveSticky and add bottom system inset padding. No model format/path changes. Same com.localsdxl.mobile and permanent key.


UI work in progress: persistent result area and automatic scroll on generation added locally. Not yet built or verified. Pending actual cancellation, prompt persistence, MNN and progressive preview.

Prompt drafts: positive and negative fields persist via queued atomic JSON writes in app support. Result panel contains progress and elapsed stage; auto-scroll on start. Cancellation remains blocked by baseline native synchronous API (no abort binding); do not claim stop implemented. No MNN/preview implementation yet. Local edits pending CI.
