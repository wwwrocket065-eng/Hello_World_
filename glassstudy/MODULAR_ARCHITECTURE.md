# GlassStudy modular architecture — implementation tracker

## Existing guarantees retained
- Existing RSA SHA256 signed `.pupdate` canonical manifest remains unchanged.
- Old `ui/layout.json` and model paths continue to be accepted.
- Assets are staged before activation; previous resources are copied forward.
- Pending patches are rolled back if first frame is not confirmed.
- Never store signing private keys in GitHub.

## Implemented (phase 1, source)
- `PatchResourceRegistry` validates paths and JSON configuration envelopes.
- `PatchManager.readResourceOrNull` can resolve signed resources from pending, active and APK-bundled assets.
- `PatchManager.readModuleConfig` returns a module's versioned JSON configuration.
- Signed patches may include `modules/<id>/{config,manifest}.json`, `catalogs/*.json`,
  `scenes/*.json`, `shaders/<id>/*.glsl|mat`, `textures/<id>/*.(png|jpg|ktx|ktx2)`,
  `ui/*.json`, `models/*.glb`.
- These are DATA assets, **not** dynamically executable native code.

## First consumer implemented
- Camera and lighting now read `modules/camera/config.json` from signed staged or active assets.
- Zoom limits, gesture sensitivities, FOV, clip range, brightness limits and light intensity are patchable.
- Bundled module configuration uses unchanged legacy defaults; absence of an external patch leaves behavior intact.
- Invalid camera module settings abort `applyUiLayout(candidate)` before patch activation.

## Render consumer implemented (source only)
- `modules/render/config.json` now controls camera exposure, FXAA and IBL intensity.
- Renderer module validated before activation through `applyUiLayout(candidate)`.
- Skybox color fields are reserved; runtime skybox replacement is NOT implemented yet.
- GitHub Actions build / device test have NOT been confirmed for this revision.

## Still to implement / verify
1. Android Gradle CI build and runtime install of an independently signed patch containing module resources.
2. Per-module API/consumer interfaces and lifecycle in the renderer, scene, UI and diagnostics (camera consumer started).
3. Transactional schema migration and module dependency/version checks, per-module size quotas and resource-specific validators.
4. Preview/commit/rollback for modules that touch Filament, with error diagnostics.
5. Fully declarative buttons/styles/actions and runtime redraw, then diagnostic/copy button fix via a separate patch.
6. Additional extensibility only through predefined native hooks and safe declarative scripting.
7. Signed APK release using the existing certificate; patch signing key is external to repo.

## Limits
Android permissions, manifest, DEX, libraries and new native APIs may require a rebuilt APK.
A patch can carry a shader or model without making it active until a renderer consumer supports it.
Never claim full modularity merely because the updater accepts more file types.

## Diagnostics consumer implemented (source only)
- `modules/diagnostics/config.json` controls whether the live report includes patch version, camera or render module values.
- Defaults disable these additional sections, preserving the existing diagnostics appearance.
- Actual values come from the active native camera/render modules; JSON cannot forge measurements.
- Material overrides still require verified Filament material bindings; no materials consumer is claimed yet.
- CI and device validation pending.

## Material variants (source implementation)
- Signed `modules/materials/config.json` maps existing object/LOD selections to allowlisted `models/*.glb` variants.
- The variant GLB carries its own glTF PBR materials, loaded through the existing Filament GLB loader.
- A referenced resource must exist at render time, or staged patch application is rejected.
- Fine-grained live per-property material parameters and material-only overrides are NOT implemented.
- Build and device tests remain necessary before release.

## Material scalar overrides (needs build and device verification)
- `modules/materials/config.json` may specify `overrides.metallic`, `overrides.roughness`, and `overrides.reflectance` in [0,1].
- Android code applies these values to loaded Filament material instances; omitted settings preserve original GLB material parameters.
- Filament material packages differ in supported parameter names: test with our actual GLBs. Transmission / IOR are not yet patchable in this handler.
- Compilation, Android smoke test and signed patch activation are NOT confirmed.

## Transmission / IOR override phase (source only)
- The optional signed `modules/materials/config.json` `overrides` object now accepts `transmission` in [0,1] and `ior` in [1,2.5].
- The handler checks `material.hasParameter(name)` before writing a scalar. Compiled glTF material features cannot be introduced into a material that lacks them merely by setting a value.
- This code MUST pass Filament 1.57.1 compilation and actual transparent-glass device tests before release. Successful CI was not verified here.

## Patch preflight hardening
- Full resulting module snapshot (including inherited modules) is validated before activation.
- Variant model dependencies must exist in staged assets or bundled APK resources.
- Missing assets cause staging rejection rather than a later selection-time crash.
- Android compilation and runtime smoke test are still unverified; no release APK should be distributed yet.

## Crash-loop prevention
- If a pending resource patch is rolled back after an unconfirmed launch, remember that patch version as `blockedAfterCrash`.
- Background update checks skip exactly that version rather than downloading and crashing repeatedly; a later version may install.
- A successful confirmed frame clears the quarantine. Local explicit patch import remains available for recovery.
- This is a source-level safeguard; Android build and device testing are still required.
