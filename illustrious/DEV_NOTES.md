# Local SDXL on Xiaomi 15T

Goal: fully offline Illustrious XL v2.0 generation; Russian UI, in-app resumable model download with SHA256, visible generation progress, memory/time diagnostics. First test without LoRA.

2026-11-09: GitHub connector access and repository write permission verified. Local Flutter download returned 404; use GitHub Actions instead. This is a build toolchain probe of pinned upstream Local-Diffusion 184b7f92cf2f810e7d5eb4b04b190a5da829005f, NOT a completed customized APK. Downloader, permission cleanup, localization, Android 16 native compatibility and Mali performance still need implementation/verification. No device generation tests yet.

Official model: OnomaAIResearch/Illustrious-XL-v2.0 on Hugging Face; file Illustrious-XL-v2.0.safetensors, approximately 6.94GB; SHA256 c2a1a3eaa13d4c107dc7e00c3fe830cab427aa026362740ea094745b3422a331.

Build: GitHub Actions .github/workflows/illustrious-android.yml. Output baseline artifact is explicitly NOT FINAL. No model weights are downloaded during CI.
