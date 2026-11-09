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
