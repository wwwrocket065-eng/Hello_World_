# GlassStudy — DEV_NOTES / история причин сбоев

Дата последнего обновления: 2026-10-19. Вносить записи сюда при каждом важном изменении.

## Исправленное

- APK с новым подписью не обновляет установленный APK: неизменными должны быть package/applicationId и оригинальный сертификат. Проверять cert fingerprint и versionCode до выдачи.
- APK v0.2.10 ранних выпусков вызывал блокировку Play Protect после добавления установки APK из приложения. Разрешение/инсталлятор удалены; не возвращать автоматически.
- Огромный голубой фрагмент стакана при v0.2.11—0.2.18: `ModelViewer` Filament с `manipulator=null` создавал fallback camera manipulator и вызывал `camera.lookAt` поверх настроек нашего `MainActivity` на каждом кадре. Отдельная реализация `GlassStudyModelViewer` решила конфликт; пользователь подтвердил целиком видимый объект в v0.2.19.
- Настройки приложения UI отрывались от APK (UI менялся только в Kotlin): добавлены `ui/layout.json`, `UiLayout.parse`, динамические `uiButtons` и контролируемые действия, конфигурация по подписанным `.pupdate`. Будущие параметры выносить в JSON по возможности, сохраняя строгое whitelist/validation.
- При UI изменениях игнорировали активную старую UI-конфигурацию патча. Не забывать публиковать новый подписанный патч вместе с новой версией базовой схемы.
- Системные панели Android и камера перекрывали заголовок: использовать WindowInsets, fullscreen flag по патчируемой конфигурации, проверять на устройстве Android 16.

## В текущем процессе: диагностика

- У пользователя был неудобный системный диалог с длинным заголовком и нижними «КОПИРОВАТЬ»/«ОК».
- Требование: кастомный PopupWindow, стиль v0.1.22, полупрозрачная светло-графитовая рамка, тёмно-синий report, шапка только «Диагностика» + framed copy; тап снаружи/по чипу/другому значку закрывает, другая кнопка выполняется сразу.
- На HEAD 2026-10-19 код v0.2.26 уже использует PopupWindow, но ещё НЕ считать проверенным телефоном; сначала CI→sign→Android test.
- С осторожностью проверить клики вне окна, фокус/нажатие Back, IME, попадание пальцем в toolbar при `PopupWindow.setTouchModal(false)`, выделение скроллируемого текста, не перекрывать нижние контролы.

## Не трогать без запроса

- Физический материал стекла визуально ещё требует отдельной доводки (в некоторых патчах стекло белое/фарфоровое), но текущая задача пользователя — интерфейс.
- Не возвращать удалённые кнопки «Применить»/«Экспорт».
- Не подменять рабочую систему автообновления заглушкой. Откат должен оставаться.
- Не генерировать картинки вместо реальных правок APK/.pupdate.

## Рабочий цикл предотвращения регрессий

Сверить ветку HEAD → короткий план → правка → regression test → GitHub Actions build → оригинальная подпись APK / RSA подпись patch → аудит → Android test → записать причину/решение. После любых ошибок сборки читать настоящие логи, а не гадать.

## 2026-10-19 — Scene controls / 0.2.29
- Baseline v0.2.28 original certificate, working automatic signed .pupdate 20028 remains the rollback/reference point.
- Replaced the third top reset icon with a scene glyph (isometric plane + sphere), without a text label.
- Scene popup separates ground (checker/white/none) and skybox (gray/studio/white/dark/custom RGB). Preferences persist locally.
- Floor is independent glTF geometry, not a wireframe toggle, and has its own ResourceLoader to avoid canceling main-model loads.
- Existing installed 20028 ui/layout.json is migrated at runtime to the scene action; old user styles otherwise preserved.
- Studio background uses bundled KTX environment; it is not a confirmed pixel-for-pixel reproduction of the old web viewer.
- Latest verified Android CI: workflow run 39189400749, success. APK 0.2.29 was signed locally using the ORIGINAL APK certificate and verified via APK Signature Scheme v2, byte-level digest, and ZIP CRC.
- Remote signed patch feed remains 20028 on purpose. DO NOT publish scene UI patch to users of 0.2.28: that APK does not implement openScene and will reject it.
- Pending device smoke test: GLB floor drawn, checker placement, studio Skybox visuals, scene reopening, and preservation of IBL and model selection. Keep the old signed 0.2.28 APK available.

## 2026-10-19 — Scene UI layout and window unification (CI pending)
- Scene sections now default to skybox ABOVE floor. Signed scene module config gains validated sectionOrder, skyboxOrder and floorOrder; this supports rearranging scene options without APK.
- Shared GlassDialogFactory handles themed scene selection, RGB picker, main menu, patch confirmations/rejection, and model-load errors.
- Removed duplicate stock Android dialogs for these flows. Diagnostics retains its specialized popup and is not yet unified.
- Existing top and bottom toolbar use patchable uiButtons arrays; scene dialog order is now patchable independently.
- Limitation: universal drag/reorder for controls in every type of window is NOT finished. Planned generic declarative window schema and adapter.
- Keep published feed 20028 unchanged until user installs and confirms a new original-key signed APK. Older APKs do not contain these action handlers.
- Regression: earlier test was brittle after declarative scene options; updated to check option map and scene ordering. GitHub Actions run 39197621546 pending at time of note.

## 2026-10-19 — Common window creation refactor
- Replaced remaining ad-hoc MainActivity AlertDialog with GlassDialogFactory.message.
- Moved diagnostics PopupWindow construction/lifecycle and window flags into GlassDialogFactory.popup. Diagnostics content and its nonmodal behavior remain unchanged.
- Shared menu, error messages, RGB sky customization and scene panel now use same window creation owner; avoids duplicate Android window boilerplate.
- Added CI assertions forbidding direct MainActivity AlertDialog.Builder and PopupWindow(outer,...); updated old assumptions about setTouchModal and dismiss listener location.
- CI run 39198244536 pending; do not release unsigned or untested APK.
- Remaining structural goal: central patch-configurable window theme and declarative placement of interior controls across specialized windows (current scene section/item order and toolbar order already patchable).

## 2026-10-19 — Signed unified window styling
- Added WindowStyleConfig and module path modules/windows/config.json. Colors, control selection state, padding, radius, row height, and modal width are schema-validated.
- GlassDialogFactory show/menu/message now reads common theme. Scene and Skybox RGB panels read common theme colors and corner radius.
- PatchManager validates an individual windows module and validates the inherited merged snapshot before activation; backward-compatible default theme when no module present.
- Existing SceneModuleConfig controls section/item order; UI layout json controls top/bottom toolbar order.
- Universal arbitrary repositioning inside every diagnostic/specialized view is still not implemented; do not claim full declarative layout support.
- CI verification run 39198584069; leave old signed feed untouched until original-key APK/device verification.

## 2026-10-19 — User-requested patchable window titles
- WindowStyleConfig now validates and parses a signed `titles` map under `modules/windows/config.json` with known keys: scene, skyColor, diagnostics, mainMenu, patchInstalled, patchRejected, patchError, modelError.
- MainActivity consumes all title values rather than hardcoding their rendered window headings. Existing patches lacking this map get legacy defaults; no new APK for subsequent renames once the compatible base is installed.
- Unknown keys, control chars, empty titles and titles over 64 chars are rejected.
- Regression tests added; CI run 39199028508 pending at this note.

## 2026-10-19 — Shared window-button behavior (no immediate reopen)
- Diagnosed report reopening on outside-tap + non-touch-modal PopupWindow: dismissal may reach the underlying diagnostics button in the same gesture.
- Added 280 ms dismissal guard for diagnostic reactivation; direct repeat press closes once. Switching to another action closes diagnostics first.
- Created shared GlassDialogFactory active-dialog registry keyed by window action for Scene and Menu, ensuring repeat press closes the current dialog rather than reopening and button-switch closes the old dialog.
- Versioned windows module now includes validated windowClickModes for diagnostics, openScene and menu with toggle / openOnly / closeOnly modes; default toggle. Signed patch can change these policies after installing a compatible APK.
- Existing declarative uiButtons already allow remapping toolbar buttons to validated known actions via signed UI patch; arbitrary executable actions remain disallowed.
- CI run 39199849807; awaiting build before any signed APK publication.

## 2026-10-19 — Contextual interactive UI
- Added OverlayModuleConfig and signed `modules/overlay/config.json` with validation in staged patch and merged snapshot.
- First live control: a contextual brightness SeekBar appears when lighting mode is active, changes Filament light brightness through updateCamera, disappears when disabled.
- Supported signed settings: enabled, showWith (light/always/never), label, bottomMarginDp and widthDp.
- Uses safe native Android widgets and a validated builtin binding; no untrusted executable code.
- Extending to arbitrary per-feature button/slider/toggle definitions and reusable control registry remains a separate architectural phase; do not misrepresent this as universal yet.
- CI run 39202174451 pending at note time; existing published patch feed remains unchanged.

## 2026-10-19 — Independently configurable overlay colors
- OverlayModuleConfig accepts signed optional #RRGGBB settings backgroundColor, borderColor, textColor, progressColor, thumbColor; omitted values inherit unified GlassDialogFactory theme.
- Updated light-mode brightness SeekBar and label to consume the independent colors.
- Regression coverage verifies config keys and consumer bindings; Android CI run 39202725868.

## 2026-10-19 — CRITICAL: rejected 0.2.30 APK; fixed signing with official Android tool

- On Xiaomi Android 16, both original 0.2.30 APK and "FIXED" APK failed with "invalid package (e.g. corrupted)".
- Source CI #303 succeeded and ZIP CRC checked, but those are NOT APK signature verification.
- The home-grown `/mnt/data/.glass_sign_work/signapk.py` produced MALFORMED APK v2 signer records. Its corresponding home-grown verify.py incorrectly said PASS. Enlarging APK signing-block padding did NOT help.
- Downloaded `apksigner.jar` from Android build-tools on the CI runner (workflow `.github/workflows/glassstudy-build.yml` now exports Android verification tools). Official `apksigner verify --verbose --print-certs` rejects both original 0.2.30 APKs with `Malformed APK Signature Scheme v2 signature record #1` / `Malformed signer block` while confirming 0.2.28 and 0.2.29 verify successfully.
- **Permanent policy:** never release APK based on own signature-verifier; sign APKs using official Android build-tools `apksigner`, verify with that same official tool, compare certificate fingerprint with old installed release (SHA256 `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc`), check ZIP integrity and native library alignment. Private key remains local, never uploaded to GitHub.
- The source PEM private key must be converted to PKCS#8 DER for official `apksigner --key`: `openssl pkcs8 -topk8 -nocrypt -in apk_key.pem -outform DER -out apk_key.pk8`. `apksigner sign --key apk_key.pk8 --cert apk_cert.der --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled false --out signed.apk unsigned.apk`. Then `apksigner verify --verbose --print-certs signed.apk`.
- Repaired release: `GlassStudy_3D_LAB_v0.2.30_VERIFIED_ORIGINAL_KEY.apk`, source CI #303, SHA256 `92ff5e9eba043bcd77746b9bdedeab8bc1307da14aec7564fc58538a30b564e0`, 11,439,731 bytes. Official apksigner: `Verifies`; v2: true, one signer; cert matches prior 0.2.29. ZIP CRC good and all 3 native libraries 4096-aligned.
- Device install of this repaired release still needs user confirmation; preserve installed previous APK until confirmed. Never publish private signing keys or even PKCS8 intermediate.

## 2026-10-19 — Rich modular windows milestone
- Added strict signed-JSON `windows[].elements` controls: headings, text, safe buttons, switches (lightMode/grid/fps), and brightnessPercent sliders. Old `windows[].buttons` patches continue to parse.
- Central renderer `PatchWindowRenderer.kt` handles all declarative panel presentation; `MainActivity` provides only explicitly whitelisted state bindings. No arbitrary code, intents or reflection through patches.
- Preserved patch, Scene, main menu and diagnostics windows across orientation changes without recreating the Filament renderer; keep checking this on Android 16.
- Rich window demo: `PROJECT_RULES/RICH_WINDOW_EXAMPLE.json`. Use its `windows` object in a complete `ui/layout.json` and a validated `uiButtons[].action="openWindow:lightControls"` to expose it.
- CRITICAL COMPATIBILITY: Do NOT publish `windows[].elements` into the shared live .pupdate feed until a new ORIGINAL-KEY-SIGNED APK implementing the rich renderer is installed. Existing v0.2.30 builds only understand legacy `buttons` and would reject the patch.
- Original-key signing, actual .pupdate installation/rollback and Android device smoke tests remain separate release gates, beyond successful source/CI compilation.

## 2026-10-19 — Axis-locked light gestures (source milestone, not released)
- Confirmed `UiInteractionController` was already wired to the activity; older handoff claiming it was unconnected was stale.
- Added an independent `LightGestureController` that locks the initially dominant swipe axis until finger lift; second-finger gesture resets the lock.
- Light elevation now spans 0–180 degrees and azimuth wraps at 360 degrees. Pinch brightness uses additive 0–200% adjustment so 0% can be increased again. Camera gestures are kept separate.
- Regression checks updated for input arbiter. **Passive indicator rendering and replacing the contextual slider remain to do.** No release APK or live signed patch published at this stage.

## 2026-10-19 — Lighting settings menu requirement (not yet implemented)
- Replace the hamburger/list toolbar icon with a gear icon labeled «Настройки».
- Settings menu should contain only style, color, and animation choices for the three lighting indicators; remove model, camera reset, and diagnostics from that menu. Diagnostics stays available through its own top chip and Scene through its own top button.
- Keep selection and visual options patch-compatible in the common UI and settings architecture; do not claim the gear or indicator styles are already implemented.

## 2026-10-19 — Major modular UI stage: toolbar and settings gear (CI pending)
- Extracted creation, layout, accessibility labels, animations, click wiring and active highlighting for both toolbar slots into UiToolbarController. MainActivity now supplies a typed Host for dispatch, state and styling; no parallel action implementation added.
- Settings gear replaces the three-line menu. Main menu has only the lighting-indicator style entry; diagnostic/scene/object actions stay on their dedicated controls. Existing signed three-item menu configurations are safely migrated to the new single entry, rather than rejected.
- Added persisted, validated LightingIndicatorStyles selection for classic/minimal/technical. This stores presentation choice only; the future passive indicator renderer must consume it before asserting visible style changes.
- Updated stale gesture-literal static regression to current implementation. Historical signed patch feed stays unchanged; do not publish new schema to older Android clients.
- Required: Android Gradle CI and real-device smoke test; no signed APK has been produced by this commit.

## 2026-10-19 — GlassStudy 0.2.32: lighting HUD (source, CI pending)
- LightGestureController locks one-finger axis and provides 0–180° elevation, 360° azimuth, and additive pinch brightness 0–200%.
- LightIndicatorView now displays real Filament lighting controls using the same shared animation renderer: brightness rail with moving rotating hollow 8-ray sun and percent, elevation arc, azimuth compass with degrees and shadow. HUD consumes no touch; it fades after release. Legacy on-screen brightness SeekBar removed.
- Settings-only gear has classic/minimal/technical user-saved presets; selection now visibly affects the HUD renderer.
- Source version 0.2.32. Not yet approved for release: CI build, actual original-certificate APK signing, Android 16 smoke test and separately signed patch compatibility remain necessary. Do not publish old live feed 20028 differently yet.

## 2026-10-19 — Per-indicator style/RGB preferences
- One gear menu item opens the themed lighting indicator settings panel. Each of azimuth, elevation and brightness has its own saved classic/minimal/technical style and an RGB picker; resetting returns to signed module color defaults. Legacy global selection remains a compatibility fallback.
- LightingIndicatorStyles only persists validated user preferences; LightIndicatorView remains a passive renderer. Color defaults can be changed through signed overlay module, and user overrides continue to take priority.
- This is source-only work until Android build and original-certificate device smoke test pass.

## 2026-10-19 — v0.2.33 large architecture stage: Scene controller and shared settings windows
- Extracted scene state/persistence and Filament Skybox + floor lifecycle from MainActivity into SceneEnvironmentController. Existing floor GLB behaviour, studio IBL fallback, favorite modes and persisted RGB restored. Invalid stored modes fall back safely; failed floor renders switch to none with persistence.
- Extracted Scene and Skybox RGB window creation to SceneSettingsPanel. It consumes a typed State interface; signed SceneModuleConfig controls section and option ordering. The shared GlassDialogFactory now supplies themed panel/option controls, avoiding duplicate widget styling and window creation.
- Activity only wires Scene controller, settings panel and applies validated bundle; scene renderer operations no longer live in MainActivity. Version 0.2.33/20033. Added structural regression assertions and retained legacy signed UI/scene patch support.
- Release feed 20028 intentionally unchanged. GitHub Android CI compilation and real-device smoke test must succeed; only original certificate may sign installable APK.

## 2026-10-19 — v0.2.34 diagnostics subsystem refactor
- Extracted diagnostics collection, on-demand numeric formatting and real GLB/Android/frame measurements into DiagnosticsReportService; live scene snapshot is an immutable DiagnosticsReportState, rather than activity fields read asynchronously.
- Extracted themed single-title/copy, non-modal outside-click-safe PopupWindow into DiagnosticsPopupController; preserves copy-without-dismissal, two controls in header, scrollable Russian report and exact last-dismiss timestamp used by UiInteractionController toggle debounce.
- MainActivity passes live state, redirects user actions to independent popup controller, retains shared action registry and existing signed WindowStyleConfig ordering. GlassDialogFactory exposes one shared themed surface helper rather than a second window renderer. No new permissions or arbitrary patch execution.
- Version 0.2.34/20034; added static regression coverage, CI build & on-device Android 16 checks required. Existing live signed patch feed 20028 untouched. Original APK certificate needed for any installable release.

## 2026-10-19 — v0.2.35 camera/light render separation
- Introduced CameraLightController as the single owner of orbit, gestures, light direction/intensity and Filament projection, with passive indicator event callbacks. Existing LightGestureController axis lock and 0–200% additive pinch preserved, including recovery from exactly 0%.
- Introduced RenderEnvironmentController for actual Filament exposure, FXAA, optional studio KTX indirect light and ambient occlusion; driven by signed validated RenderModuleConfig. Skybox/floor remain owned by SceneEnvironmentController.
- MainActivity now delegates SurfaceView touch events, obtains typed camera/light snapshot for diagnostics and applies validated CameraModuleConfig/RenderModuleConfig. No native code added through .pupdate. Source version 0.2.35/20035, live feed 20028 unchanged.
- CI compile and real Android 16 touch/device tests are mandatory before a signed release using the ORIGINAL certificate.

## 2026-10-19 — v0.2.36 model/material subsystem consolidation
- ModelCatalog/ModelSelection: one list of built-in identifiers, display names and levels; selectors and diagnostics now use the same catalog, not copied arrays in MainActivity.
- ModelMaterialController: single owner of signed GLB variant resolution, patch candidate material schema, glTF material overrides, direct-buffer loading, GLB preflight and last-known-good restoration if Filament asset replacement fails. Model rendering never consumes unsigned arbitrary resources.
- Grid preference and selectors commit only after a successful model load; failed model selections revert to the working selection. Candidate loading uses candidate catalog/overrides consistently before activation; updater's existing reject + old UI/model restore remains in control of patch rollback.
- MainActivity delegates model loading/materials and no longer owns byte buffers, variant mapping or asset lifecycle. Source 0.2.36/20036; signed live patch feed 20028 remains unchanged. On-device Android 16 check and original-certificate apksigner still required before an installable release.

## 2026-10-19 — v0.2.37 single signed UI chrome rendering and presentation boundary
- Extracted color/style application, logo measurements, selector adapters, top/bottom panel backgrounds, toolbar rendering, status hints, lighting indicator visibility/appearance, window metadata and immersive system bar behavior from MainActivity into UiChromeController. No duplicate theme resolver was added: UiLayout/UiThemeEngine supplies validated colors, GlassDialogFactory remains the shared popup/window policy.
- UiChromeViews is an explicit typed view port bound once after UI construction. UiChromeController contains no Filament, updater, model loading or patch activation. MainActivity only wires feature modules and delegates presentation application after PatchUiBundle validation.
- Diagnostic popup style and report labels/sections now read from the same applied UI chrome snapshot; reduces color drift after signed patch updates. Contextual HUD remains passive and only appears during actual light gestures.
- Version 0.2.37/20037, published signed feed 20028 unchanged. CI compilation and Android 16 device interaction testing required; an installable update still requires matching original APK certificate.

### v0.2.37 CI finding and preventive check (2026-10-19)
- Root cause: a scripted block extraction duplicated the opening Kotlin doc-comment token on the MainActivity applyUiLayout header, leaving an unclosed nested comment. Android compiler reported many secondary 'Unresolved reference' messages plus primary 'Unclosed comment'; the referenced methods were actually present below the malformed comment.
- Fix commit aad69b78: restore a single well-formed doc comment, without rewriting app logic or disabling validation. Always identify the FIRST syntax error before treating downstream missing references as separate bugs.
- New static regression gate checks for the accidental nested doc-comment header and for presence of key lifecycle/patch/diagnostics methods before the long Android compile. Continue to rely on actual Kotlin CI compiler as authoritative.

## 2026-10-19 — 0.2.38 signed patch integration and rollback hardening
- Introduced PatchActivationCoordinator: single ordered transaction with stages VALIDATION → MODEL → UI → ACTIVATION; runs entirely on the UI thread, never publishes an unvalidated or unrendered preview as a confirmed installation.
- Rechecks the complete PatchUiBundle before mutating live Filament assets or Views, catches staged model/UI/persistence errors, rejects the stage, attempts both previous UI and GLB restoration independently, and exposes the exact failed phase in PatchManager.updateStatus, visible in existing diagnostics. An incomplete recovery explicitly asks for restart.
- Discovered race: importing a second signed .pupdate while a prior one is installed but not yet confirmed could make the unconfirmed version the 'previous' rollback target. PatchManager.activate now refuses activation whenever 'pending' is nonzero; this protects the previous confirmed rollback base. Existing four actually rendered frames remain required to confirm pending patches.
- MainActivity now delegates both online and local patch application to the coordinator. Version 0.2.38/20038 source only; original-certificate APK and Xiaomi Android 16 installation/gesture/recovery smoke tests remain separate release gates. Existing published feed 20028 unchanged.

## 2026-10-19 — v0.2.39: real signed-patch and APK preflight CI gate
- Added tools/test_patch_integrity.py: verifies actual published 20028 feed SHA256 and RSA signature against the PUBLIC_KEY_BASE64 embedded in PatchManager, using the system OpenSSL and the original canonical manifest bytes. Negative tests reject modified payload, recomputed-but-unsigned SHA, traversal path, duplicate resources, wrong version, wrong format, bad signature and changed feed hash. No private key/network access.
- Added tools/test_apk_preflight.py: after the Android CI assembleCiUnsigned step, opens the generated APK and verifies ZIP integrity, uniqueness, Android package ID, manifest VERSION_NAME/CODE, resources.arsc 4-byte alignment, every uncompressed JNI .so 16KiB alignment, official zipalign validation, and that apksigner rejects it as UNsigned. An unsigned APK is not a user-installable release.
- Both Android build workflows run the real published patch verification; the main unsigned APK workflow additionally runs APK preflight. No debug-key signature may be mistaken for original release signing.
- Added PatchManager.stage() early pending guard: a second patch is rejected before signature-stage file-copy/render work while another installation awaits actual rendered-frame confirmation. PatchManager.activate() retains its independent guard. This avoids building a rollback chain on an unconfirmed version.
- Source 0.2.39/20039. Does not modify live feed 20028 or any prior signed patch. Still requires original certificate signing and user Android 16 tests before release; isolated patch tests cannot simulate app process death.

### 0.2.39 original-key signed APK release validation — 2026-10-19
- Signed the exact CI-unsigned 0.2.39 APK from GitHub Actions run 39321129964 with the pre-existing GlassStudy APK private signing key from the protected user Library. No new key and no repository secrets. v2 signer certificate was checked against the installed baseline v0.2.30 and matched the published original SHA256 35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc.
- Result: GlassStudy_3D_LAB_v0.2.39_ORIGINAL_KEY.apk, SHA256 dab14762145b9707de53c8288ea79fd2b8456b8f0d18fb9969b360e1ca06025d, size 11,488,883. Native .so aligned 16KiB, resources.arsc aligned 4B, payload unchanged, valid v2 signature. Signed file provided as a conversation artifact, NOT uploaded to public GitHub.
- Source-only architecture score remains an estimate (98% before this release work). No device validation has been claimed: the next gate is user Xiaomi Android 16 update installation, camera orbit, light azimuth/elevation/brightness HUD, settings menu, real diagnostics and signed patch rollback.

## 2026-10-20 — v0.2.40 compact four top icons + rounded gear valleys
- Root cause of oversized toolbar: UiToolbarController delegates topButtonSizeDp to AdaptiveUiConfig.touchSizeDp, which clamps all top buttons to at least 44dp. v0.2.39 patch alone cannot make painted buttons <44dp or change hardcoded gear geometry.
- UiChromeController now draws an InsetDrawable centered within the original 44dp (or responsive) hitbox for LabIconView top buttons, using topButtonSizeDp as VISUAL square width (default 35dp). Full hit target remains accessible; bottom buttons remain unchanged. Signed UI patches can now adjust visual square without Java/Kotlin updates.
- LabUiIcons gear has eight rounded teeth (quadTo outer caps and inner fillets) instead of 24 jagged line segments. New patchable numeric gearInnerRoundness in UiLayout schema (safe range 0.30..1.00; default .80 for older signed patches). All four top icons remain chip, sun, scene and gear, with separate existing actions.
- Incremented native APK v0.2.40/20040. Release gates: GitHub CI, original cert signing, and Android 16 visual/tap checks. Independent .pupdate 20029 should change topButtonSizeDp to 32, topIconScale to .76 and gearInnerRoundness to 1.0; it requires the new APK to show visual/gear changes fully. Preserve previously published 20028 until tested.

## 2026-10-20 — Fast modular patch publishing workflow
- Added tools/fast_patch.py for ALL current signed resource domains: ui/layout.json; camera/render/diagnostics/materials/scene/windows/overlay configs; allowlisted model GLB, scene/catalog JSON, shader/texture resource files. Builds only changed resources with a single canonical signed RSA manifest; no Gradle, no APK and no repetitive manual assembly.
- --set MODULE.property=value validates that the field already exists and preserves the configured property type; --file path=localFile packages any allowlisted resource. --dry-run needs no private key. Only original PATCH RSA key (distinct from APK upload key) may sign real patches. Publication is explicitly separate and feeds must be written only after patch files; never auto-publish without review.
- SAFETY: The source baseline is reconstructed from all historical signed published patches, verified against the app's embedded public key; modifications to one parameter do not reset previously patched values in the same config. Android PatchManager.stage already carries forward all signed assets. New fast-patch smoke tests run in GitHub Actions.
- This supports only declarative controls consumed by already installed APK Kotlin logic. New native effects/unknown UI actions/new rendering algorithms still require a normal signed APK. Do not claim otherwise. Live feed remains at 20028 until the user chooses to release the pending appearance patch.

## 2026-10-20 — Auto-update failure diagnosed from Android 16 diagnostics: source and feed mismatch
- User device on native v0.2.40 showed active/confirmed/server patch 20028 after receiving a standalone signed 20030 file. The primary cause was server updates/v2/latest.json still advertising 20028; an arbitrary .pupdate sitting in Downloads is NOT scanned silently by the current Android implementation (only explicit ACTION_VIEW/ACTION_SEND import and server check at Activity onCreate).
- Published existing RSA-verified 20030 patch, preserving exact original bytes and SHA-256 73a6bbb2034a9390aa9f84cf17863149107930970c1c7b19eb2caef5aed679cc; committed GlassStudy_Filament_20030.pupdate + latest.json together at 98e1c8570bb62bfbc3dd7417291c5c8ed4fddfa1. Existing v0.2.40 BuildConfig.UPDATE_BASE_URL points to this branch's v2 feed; no APK rebuild needed for server-originated updates. The patch changes only UI icon scales and shape settings.
- First CI cycle failed because test_fast_patch.py hardcoded the next version as 20030 and expected a value that is now already published. Corrected test to derive NEXT_VERSION=latest.patchVersion+1 and use a non-noop UI parameter; commit 51e795eab876688a87bf2219405878f72fdaceca. Always derive test versions from live feed, never hardcode current patch values.
- Verification needed: on phone FULLY stop GlassStudy and launch fresh; inspect diagnostics after update: installed 20030, confirmed 20030, server 20030. Do not claim local Downloads auto-scan exists. Android 16 scoped storage generally requires an explicit SAF grant or opening/sharing the file; consider a one-time folder grant in future APK if requested. Do not request broad file management permission.

## 2026-10-20 — 0.2.41 full configurable toolbar spacing
- Corrected remaining hardcoded UI gaps in UiToolbarController. Top leading margin, top per-button gap, bottom per-button gap and bottom row gap are independent validated UiLayout properties. Existing old signed patches without these settings get exact legacy defaults (4/4/8/8 dp).
- New validated JSON params: topButtonGapDp 0..24, topButtonLeadingDp 0..32, bottomButtonGapDp 0..32 and bottomRowGapDp 0..32. Layout calculation includes total dynamic top gap; actual touch target and icon drawing remain separate. UI update can apply all gaps through a signed .pupdate once Android APK v0.2.41 is installed.
- Old APK v0.2.40 cannot understand these new Kotlin-only layout properties; one original-key signed 0.2.41 APK required. Do not misrepresent a resource-only 20031 patch as capable of changing spacing on 0.2.40.

- Screenshot geometry follow-up: old toolbar button center-to-center separation is appreciably smaller than new 44dp accessible hitboxes allow with nonnegative margins. topButtonGapDp deliberately permits -12..24dp, enabling small OVERLAP of adjacent full-size hit zones while keeping ~44dp actual targets and 32dp visual boxes. The first button's leading margin stays independently configurable. Check taps near the overlap seam on Android before calling perfect match.

## 2026-10-20 — v0.2.42 all-button patchable layout
- Signed UiButtonSpec now supports per-ID widthDp, visualSizeDp, iconScale, offsetXDp, offsetYDp, marginStartDp, marginTopDp. UiToolbarController applies per-button width/height, icon scale, XY translation and positional margins for TOP and BOTTOM controls; UiChromeController uses per-button visible square size independently of touch hitbox.
- Validation clamps ranges and rejects NaN/Infinity/unsafe values. Optional keys keep legacy signed patches compatible. Full built-in uiButtons JSON includes default attributes for fast signed modification of any individual ID. Native v0.2.42/20042 required before these fields can be used in patch data.

## 2026-10-20 — 0.2.43 local auto-update source instead of requiring GitHub
- Implemented LocalPatchDirectory using Android scoped Storage Access Framework. User grants a dedicated folder ONCE (e.g. Downloads/GlassStudyUpdates); the read-only tree permission is persisted. On next launches app scans child documents, chooses newest .pupdate version and passes only content URI to the EXISTING PatchManager.installFromUri RSA + SHA256 stage pipeline.
- App launch checks local folder first; remote GitHub patch feed is an optional fallback. A corrupt or rejected file won't stop startup; server fallback checks for updates. Settings menu includes 'Папка локальных патчей' to reassign folder. First install explains the one-time folder grant; old APK cannot auto-read third-party Downloads under Android 16 scoped storage.
- No broad MANAGE_EXTERNAL_STORAGE permission, no private-key exposure, no second patch verifier. Version 0.2.43/20043, original-key APK signature and Android 16 testing required. A direct grant to the Downloads ROOT may be unavailable on Android 11+; use a new Downloads/GlassStudyUpdates subdirectory.

## v0.2.44 — Android 16 local patch folder follow-up
- Offline update source uses ACTION_OPEN_DOCUMENT_TREE SAF, NOT MANAGE_EXTERNAL_STORAGE; Android 11+ prohibits selecting the Downloads root. User creates Downloads/GlassStudy_Patches, grants the child folder once, then .pupdate files placed there are found automatically on every cold start. Retained tree grant is saved privately; user can change folder in the Gear menu.
- Added stage() rejection of blockedAfterCrash so local patches that crashed on boot are not reapplied forever. Existing RSA+SHA validation and four real rendered frames confirmation remain authoritative. Remote GitHub feed remains optional fallback, not a requirement for local installation.
- If selected folder grant becomes invalid, reset stored grant and allow the user to reselect. v0.2.44/20044 is native-only; signed with ORIGINAL APK key for updates over 0.2.42/0.2.40.

## 2026-10-20 — v0.2.45 local patch auto-check on foreground
- Actual Android 16 SAF folder grant is already available in LocalPatchDirectory. Earlier implementation only checked once in onCreate or after folder grant, so placing a patch while the app remained alive would not trigger rechecking on returning from background.
- MainActivity now re-scans the persisted user-selected patch directory whenever it resumes after 1.2s. Existing initial check remains. Signature and SHA validation unchanged; remote feed remains optional fallback, never required for local files. Version increased to 0.2.45.
- Android 11+ cannot grant whole Downloads root with ACTION_OPEN_DOCUMENT_TREE; user must authorize a Downloads/GlassStudy_Patches subfolder ONCE. Automatic access to arbitrary Downloads without permission is unavailable. This is a platform constraint; do not claim otherwise.


## 2026-10-20 — Offline .pupdate candidate resilience
- Verified branch already progressed to 0.2.46 before this change (head ceec3f84b); do not reset to 0.2.42.
- LocalPatchDirectory previously discarded the saved SAF tree URI whenever a DocumentsProvider query threw. Temporary provider failures could therefore trigger repeated folder selection. Preserve the grant and report lastScanError instead.
- newestAbove previously returned exactly one highest-name-version candidate; an invalid/unsigned/corrupt highest file prevented installation of any other newer-than-installed patch. candidatesAbove now ranks matching files, and MainActivity tries each through existing PatchManager.installFromUri, which enforces signature/hash checks.
- No all-files access, no new update subsystem, no remote dependency when folder configured. Report scan and rejection reasons via existing updateStatus.
- Validation levels: GitHub Actions compile/tests still required; Android 16 real-device test not yet performed. Verify saved SAF grant across force-stop/reboot; downloaded MIME naming, malformed highest file fallback, RSA rejection, frame confirmation, and recovery on crash.


## 2026-10-20 — Offline toolchain audit after installing 0.2.47
- Verified GitHub branch head 46054d9d; three Android CI checks passed. Device has 0.2.47 installed per user; phone-side updater behavior remains UNTESTED.
- ChatGPT Library original backup found and materialized privately; extracted no keys into repository. Original 0.2.42 APK verifies with Android APK Signature Scheme v2. Its certificate SHA-256 equals the original backup APK certificate: 35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc; original private APK key matches certificate public key.
- Retrieved apksigner and zipalign from successful CI artifact; apksigner works. zipalign Linux binary cannot run here due to missing libc++.so; APK verification uses apksigner instead. No new key generated.
- Original signed patch 20031 verified offline with backup PATCH RSA key; each resource SHA-256 matches. A local signed test patch 20032 containing exactly the same ui/layout.json configuration values as 20031 (whitespace-only difference) was generated and RSA/SHA-256 verified for Android 16 offline-folder testing. The local container could not clone GitHub due DNS resolution failure, so this particular 20032 smoke-test file was generated by a minimal script reusing the same canonical/signature format as tools/fast_patch.py, NOT by executing fast_patch.py itself. Do not publish test patch or treat this as a replacement for fast_patch.py.
- Need Android 16 on-device trial: place GlassStudy_Filament_20032.pupdate in SAF-granted Downloads/GlassStudy_Patches; open app; inspect diagnostics for active 20032 and actual frame confirmation. On failure copy diagnostic report. Keep original installed APK/data untouched.


## 2026-10-20 — Root Download SAF restriction, legacy comparison
- Android 16 system picker explicitly refuses ACTION_OPEN_DOCUMENT_TREE for the Download root; adding READ_EXTERNAL_STORAGE does not override this restriction. The existing LocalPatchDirectory already supports persisted read permission to an allowed child folder.
- Inspected ChatGPT Library Hello_World_v0.196_Source.zip, v0.195_Source.zip and CHECKPOINT_2026-10-13.zip. These archives contain web update sources/assets, not Android Kotlin/Java or a manifest proving how old builds acquired Downloads access. Do NOT assume old app used MANAGE_EXTERNAL_STORAGE from these sources.
- Android 10+ scoped storage cannot silently read arbitrary third-party Downloads through MediaStore without appropriate ownership/access. MANAGE_EXTERNAL_STORAGE is a highly restricted broad permission and not acceptable as a default patch-updater workaround, especially for Play distribution.
- Safe current strategy: one-time SAF grant to Download/GlassStudy_Patches, or use ACTION_OPEN_DOCUMENT/ACTION_SEND to import a file. Automatic detection of arbitrary files directly in root Download requires another lawful distribution mechanism and user verification; do not claim it is implemented.
- Installed 0.2.47 and its data must remain untouched. On-device result is still unverified.


## 2026-10-20 — GlassStudy 0.2.48: optional root Download update source
- User requested Panspermia-style one-time Android special all-files access, allowing direct scan of Download for signed GlassStudy_Filament_<version>.pupdate files.
- Existing LocalPatchDirectory now supports opt-in Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION and checks Environment.isExternalStorageManager() before scanning Environment.DIRECTORY_DOWNLOADS. Retains persisted SAF as alternative. No broad permission can be silently granted.
- MainActivity opens permission settings from existing menu/first-run prompt, and rechecks updates on resume. PatchManager remains sole RSA/SHA-256 verifier and installer; PatchActivationCoordinator retains rollback.
- Compliance caveat: MANAGE_EXTERNAL_STORAGE is restricted by Google Play policy, so this APK approach is for deployments meeting the applicable policy requirements; publication approval is not established. Device behavior on Android 16 still needs user test.
- CI status and APK signing must be verified separately; original APK signing key is never committed.


## 2026-10-20 — GlassStudy 0.2.48 Android 16 Download updater build (source 8532c8da)
- Source branch `glassstudy-filament-ci`, code/test commit `8532c8da87b7035e4e1bd2d511335f8d7a79e37c`.
- Reuses `LocalPatchDirectory`: optional user-approved Android `MANAGE_EXTERNAL_STORAGE` via `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`; direct `Environment.DIRECTORY_DOWNLOADS` scan of `GlassStudy_Filament_<5 digits>.pupdate`, sort descending, only versions newer than installed; existing SAF persisted-tree fallback unchanged. PatchManager alone handles verified install and rollback.
- CRITICAL MIGRATION FIX: previous 0.2.47 persisted `localPatchPromptedV2=true`, so new permission prompt would be skipped. 0.2.48 uses `localPatchPromptedV3` and checks `!hasDownloadsAccess()`; regression assertions added in `tools/test_project.py`.
- Android Build CI 39461159622 and Filament CI 39461152605 both PASS for source commit 8532c8da. Android APK is built unsigned in public CI then signed privately OFF GitHub with ORIGINAL `private/apk_key.pem` and `private/apk_cert.der` from protected ChatGPT Library backup, converted ephemerally to PKCS#8; no replacement key generated or secrets checked in.
- Final signed 0.2.48 output (conversation artifact): `GlassStudy_3D_LAB_v0.2.48_ORIGINAL_KEY_FINAL.apk`, SHA-256 `344eeedb79f1e3ef2af42230c991196db86dd1385ffefbe7114d30784b9ea0b4`, size 11,505,267 bytes. APK v2/v3 signature valid using minSdk26; cert SHA-256 `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc`, equals original 0.2.42 certificate; 99 ZIP file payloads unchanged by signing; 3 JNI libraries 16KiB aligned and resources.arsc 4-byte aligned. Classes.dex contains localPatchPromptedV3, manifest contains MANAGE_EXTERNAL_STORAGE. Original certificate/private key never put into public GitHub.
- Local test `GlassStudy_Filament_20032.pupdate` (8,641 bytes) RSA/SHA-256 verified against exact public key compiled in PatchManager; contains ui/layout.json (5,947 bytes), designed to be visually unchanged from prior UI configuration.
- DEVICE VERIFICATION STILL REQUIRED: user currently has 0.2.47 installed. Install signed 0.2.48 OVER existing (do not uninstall); open app; grant all-files special access once in Android system settings; leave `GlassStudy_Filament_20032.pupdate` in ordinary root Download, reopen app, and check diagnostics installed patch 20032 confirmed frame. If permission UI inaccessible or patch fails, collect short diagnostic report rather than disabling updater.
- WARNING: broad all-files special permission is Android/Google Play policy restricted; release suitability for Play has NOT been established. Do not assume Google Play will accept this manifest without qualifying core use and declaration.


## 2026-10-21 — Android 16 confirmation from user
- User confirmed: «Работает, спасибо» after testing GlassStudy 0.2.48 with the one-time all-files permission and automatic local Download .pupdate workflow.
- Treat this as user-confirmed working behavior, not as an independently captured diagnostics report; exact active patch version/frame confirmation numbers were not supplied in this message.
- Keep the established Android 16 Download permission flow and original-key signing; do not replace it or regress it in future UI/refactoring changes.


## 2026-10-21 — Lighting indicator numbers and cross-project audit
- User reiterated binding development rule: every safe user-facing numeric value should be patchable through a small signed .pupdate without repeated APK builds; do NOT treat two currently reported numbers as a one-off exception.
- Source audit covered 40 app Kotlin classes: additional still-literal presentation numbers occur in LightIndicatorView (animation interpolation, label/rays/guide sizing), DiagnosticsPopupController (window widths/padding/text sizes), LabUiIcons (vector glyph stroke/geometry), LightIndicatorSettingsPanel (RGB row spacing), GlassDialogFactory (common widget padding), and SceneSettingsPanel (screen spacing). Gesture thresholds and some camera/light defaults in LightGestureController/CameraLightController, and FrameProfiler sample window, are further candidates if appropriate. These are examples, not yet fully migrated; do not claim full project coverage.
- Already patchable elsewhere: UiAnimationEngine signed per-view presets, UiLayout/UiButtonSpec dimensions and styles, typed CameraModuleConfig, RenderModuleConfig, SceneModuleConfig, OverlayModuleConfig and WindowStyleConfig.
- Added common `IndicatorVisualConfig.kt` for all three light indicators, with 18 individually bounded numeric fields each (including physical-dp x/y offsets, 0–max sun rotation, geometry, smoothing, fade times), loaded from `modules/overlay/config.json -> indicatorVisuals.{brightness|azimuth|elevation}`. `LightIndicatorView` consumes these values; `UiChromeController` applies the parsed module. Brightness default shifts right 40dp and runs 0–90 degrees for 0–200%, while other modes preserve existing rays and angular behavior by default. Tests exercise nested fast_patch --set and module schema. This is NEW NATIVE behavior requiring 0.2.49 original-key APK before these new keys affect an installed 0.2.48.
- Remaining separately coded presentation numbers should be migrated in structured stages, not via sweeping untested substitutions. Preserve Android 16 Download permission and signed patch activation.
- CI, signed release, and on-device demonstration have not yet been marked completed at the time of this note.


## 2026-10-21 — Verified original-signature native 0.2.49 + signed numeric overlay 20033
- Native APK source: GitHub branch `glassstudy-filament-ci`, commit `512c043bc298ae5f715c80eddf8849da3d378d1d`. CI Android Build `39642004670` SUCCESS (static regressions, fast-patch dry-run, official native compilation and 16 KiB ZIP verification); Filament `39642004677` SUCCESS.
- Final APK conversation artifact: `GlassStudy_3D_LAB_v0.2.49_ORIGINAL_KEY.apk`; bytes 11,505,267, SHA-256 `baf73ae99972cb61845ee2e4c63e493affcdbbe3077af0b1a3e0cfd03326a7a6`. Signed locally with original APK key protected in Library; original certificate SHA-256 `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc`, APK Signature Scheme v2/v3 verified. ZIP entries unchanged from CI unsigned, JNI libraries 16 KiB aligned, `IndicatorVisualConfig` class and existing Download permission migration verified inside DEX.
- Tiny signed offline patch artifact: `GlassStudy_Filament_20033_Brightness_Indicators.pupdate`, bytes 3,446, SHA-256 `c178632f0c24d8ff2f7f45f4160087034dda36b789e0caf444bb3898cbfb1256`. Carries ONLY `modules/overlay/config.json` with brightness offset x=46 dp and 0–200% sun rotation maximum 90°, plus existing numeric presets for azimuth and elevation; verified its RSA SHA-256 signature against the EXACT public key embedded in the 0.2.49 APK, and per-file SHA-256. Existing previously installed patch 20032 and signed UI settings remain preserved by PatchManager staging.
- New numeric modules require this ONCE-only native APK upgrade from 0.2.48, since the previous APK lacks the visual-spec interpreter. After that, individual and multiple values can be changed through `fast_patch.py --set overlay.indicatorVisuals.<mode>.<field>=...`, signed resource only. Original keys never placed in GitHub.
- Android 16 actual UI position, sun animation, APK upgrade and 20033 patch confirmation are NOT YET device-tested. Do not claim otherwise until user diagnostics/screenshots.


## 2026-10-21 — Cross-module numeric patch architecture, source APK 0.2.50
- User explicitly clarified that numerical patchability applies to ALL safe numerical parameters across UI, render, scene, camera, windows, materials, animations, indicators and diagnostics, not only brightness/light visuals. Never narrow this to an isolated setting.
- Expanded signed module consumers: camera adds nine typed numbers (home position/orientation, gesture bounds and pinch/axis thresholds); standard Skybox RGB adds nine patchable integer channels in scene; renderer supports the signed ambientOcclusionEnabled switch while existing IBL/exposure/FXAA remain patchable; windows has 12 bounded diagnostics popup geometry/text values; UI adds 2 upper-toolbar layout bounds and 9 shared visualNumbers for buttons/logo/selectors. Total 41 new numerical settings + 1 boolean; existing 18-per-indicator schema remains.
- Reused existing PatchUiBundle, UiLayout, CameraModuleConfig, RenderModuleConfig, SceneModuleConfig, WindowStyleConfig; no new update channel or arbitrary executable patch scripts. Typed parsers constrain ranges. The same fast_patch.py tool now has --list-numbers and supports several --set options across different modules within ONE signed .pupdate.
- Preserved original CameraLightController.resetHome behavior; new light home defaults apply once per Activity setup, not unexpectedly on camera reset or each resource patch. Added tests for actual Kotlin consumers and a six-resource multi-domain dry-run.
- CI root cause 1: old source regression expected hardcoded 160.0 pinch divisor; updated assertion to reference config.brightnessPinchDivisorDp. CI root cause 2: multi-module dry-run requested brightness rotation already equal to baseline 90, so correct builder omitted unchanged overlay file; test now uses 87 only to exercise a real changed value. Existing default remains 90 degrees at 200%.
- Successful GitHub Actions for source commit 828a29a4f07fecefeca1994ef28b1b7ad14e6069: Android Build runs 39671884074 and 39671877370, Filament Android run 39671877511, all success; Android tool preflight verified package/version/ZIP and 16KiB alignment.
- Final local APK signed with original GlassStudy APK signing key, NOT committed: GlassStudy_3D_LAB_v0.2.50_ORIGINAL_KEY.apk, SHA-256 9555445234c59ced2655e5977b2185e336a370de0313d12d991646fc7355a7b6, 11,521,651 bytes. Original cert SHA-256 35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc matches existing installs; APK v2/v3 signatures verified with official apksigner. Original 99 ZIP payload files hash-identical after signing; JNI payload offsets 16KiB aligned, resources.arsc 4-byte aligned. Local zipalign executable lacks its libc++.so, so pure ZIP offset/CRC verification and successful official CI preflight used; do not claim local zipalign itself ran.
- Audit and examples: glassstudy/PATCHABLE_NUMERIC_SETTINGS.md. Known remaining literals: settings panel geometry, vector-icon stroke/dimensions, certain theme/decorative values, unconsumed legacy render.skyRed/Green/Blue. Do not claim 100% source migration. Core limits/cryptographic/format constants intentionally must remain hardcoded.
- Runtime Android 16 install/functional smoke NOT yet user-confirmed for 0.2.50. Preserve last working 0.2.48 until installed over it and validated. Data-only patch cannot add new Kotlin consumer to the old APK; one native update required first.


## 2026-10-21 — Offline signed brightness-only patch 20034
- User requested a further rightward offset to test that numerical parameters can be changed via .pupdate without building a new APK.
- Based on the previously RSA-verified signed 20033 overlay resource, prepared `GlassStudy_Filament_20034.pupdate` (3,446 bytes; SHA-256 `7bb995f1dfe46f79af8015754d04ee8e99f6f5cbd52ae4ea46d96cabf7d9e297`).
- Exactly one value changed: `modules/overlay/config.json -> indicatorVisuals.brightness.offsetXDp` from 46 to 60 dp. All other JSON data including `rotationAtMaxDeg=90` remains equal to the 20033 config.
- Signed with ORIGINAL protected PATCH RSA private key held only in Library, then verified using the exact public key embedded in `PatchManager.kt`. No private key uploaded, no GitHub feed change, no native APK rebuild.
- Filename uses exact `GlassStudy_Filament_20034.pupdate` for Android 16 root Download autoscanner. Requires installed native 0.2.49 or newer; old 0.2.48 did not implement `indicatorVisuals`.
- User Android 16 runtime positioning/frame-confirmation still awaiting test.


## 2026-10-21 — Small signed brightness position patch 20035
- Following user feedback after patch 20034 (brightness offset 60dp), prepared offline signed `GlassStudy_Filament_20035.pupdate`. Changes only `modules/overlay/config.json -> indicatorVisuals.brightness.offsetXDp` from 60 to 74dp. Original brightness rotation-at-200-percent stays 90 degrees; other configuration values preserved exactly.
- Patch 20035 is 3,446 bytes, SHA-256 `92e7d7729663e9abc5c039e18930cc00487ad24041dbd39f18f3f207dbb0e460`. Verified RSA signature with the original protected PATCH signing key, source patch 20034 RSA signature, each entry SHA256 and expected JSON delta. Key never uploaded or committed.
- Distributed as a local Download-folder .pupdate for GlassStudy native 0.2.49+; server feed unchanged at 20031. Actual on-device position and confirmation pending user.

## 2026-10-22 — Brightness HUD offset 100dp (v0.2.51 + patch 20036)
- Request: move only brightness indicator from 74dp to 100dp right, keep rotationAtMaxDeg=90.
- Bug: older v0.2.50 rejected >80dp and drew offset inside a fixed 230dp Canvas, risking clipped drawing.
- Native fix in 0.2.51: validated horizontal offset range ±160dp and moved whole indicator View instead of Canvas content; all modes retained.
- Source commit ed88025f9e061038602530cd7f8183376e0614e2, three CI workflows successful: Android 39676333488 / 39676341176, Filament 39676333489.
- Verified release: GlassStudy_3D_LAB_v0.2.51_ORIGINAL_KEY.apk, SHA256 4f180ed314bda0d39d7c844b5f14b489edc98d3cf912aaac82af64d4ecb539e7, 11,521,651 bytes; original signing certificate matches past installations. Original APK app ID intact; ZIP alignment and v2 signature verified.
- Signed offline patch: GlassStudy_Filament_20036.pupdate, SHA256 0caffe2b19d0c3ec824cf97d96ba1da94f902d7573fc7b3936ea1a76eb7e2478, 3,446 bytes. Relative to signed patch 20035, only indicatorVisuals.brightness.offsetXDp changes 74→100. Rotation 90 and all other settings unchanged. Signatures and resource hashes verified.
- Install native APK 0.2.51 over existing app before placing patch 20036 in Download; older 0.2.50 rejects that signed value. Server feed unchanged at 20031. On-device UI positioning still needs user verification.

## 2026-10-22 — Native 0.2.52 persistent light indication and tap-to-hide
- User clarified: lifting fingers must keep the currently displayed light indicator on screen; ONLY a fresh gesture may replace the indicator (horizontal azimuth, vertical elevation, two fingers brightness). A single stationary tap dismisses it, without switching modes or adjusting light.
- Added pure Kotlin LightTouchSession for tap classification and multi-pointer suppression. ACTION_UP no longer unconditionally calls resetGesture/hideIndicator. Pointer-up after pinch does not hide or switch to a different single-finger axis. ACTION_CANCEL is not a tap. Explicit resetGesture on mode switch/app pause still hides.
- Signed-data numeric threshold camera.lightTapSlopDp (default 6dp, allowed 1..36dp). Source regression checks and standalone pure Kotlin motion/session test added.
- Native version 0.2.52 code 20052; preserves original Android package ID and signed Download .pupdate workflow. Existing brightness offset 100dp remains in signed asset patch 20036, no patch release or asset change needed.
- CI/signed APK/device checks required; do not declare Android device behavior verified until user tests.

- Extra edge-case fix: ACTION_UP checks final pointer displacement through the same signed-data tap-slop rule, so a fast swipe with no intervening ACTION_MOVE cannot be classified as a stationary tap.

## 2026-10-22 — Native v0.2.53 unified technical light indicators / 3D guides
- User requests: remove cast shadow under each sun; technical visual style is the only style; remove classic/minimal and every UI item for indicator style/color selection; share brightness's warm sun tint, glow, line weights and ring-gap behavior among azimuth/elevation/brightness; no azimuth or elevation glyph rotation; no sun-to-circle-center spokes. Horizontal azimuth guide is a fixed 3D circle which projects to ellipse or near-line as the camera moves from top to horizon. Elevation guide is the vertical plane semicircle, rotating with world-light azimuth. Sun glyph ALWAYS circular and undistorted.
- Added pure orthographic LightGuideProjection.azimuth/elevation with deterministic regression test in tools/LightGuideProjectionRegression.kt; uses Filament camera yaw/pitch and light physical azimuth to show the correct elliptical orbit; independent of rendering of 8-ray 2D glyph.
- LightIndicatorView draws sampled projected paths in one generic method, clips track locally around current projected sun position to prevent the orbit from passing through the sun, no spokes, no oval cast shadows, no label shadows. Applies configurable guide/sun glow using typed overlay parameters. Only brightness rotates to 90deg at 200% (same signed numerical value).
- OverlayModuleConfig.indicatorColor returns brightnessColor for all three modes; legacy azimuthColor/elevationColor keys remain parseable for compatibility with already-signed local patches but are deprecated for visual rendering. Existing persisted style and RGB color preferences have no further effect; obsolete LightingIndicatorStyles and LightIndicatorSettingsPanel classes deleted.
- App gear now retains one item "Папка локальных патчей". WindowStyleConfig migrates signed legacy menuOrder=["indicatorStyle"] and old 3-entry list to ["localPatches"] to avoid rejecting an already installed verified patch. Bundled windows/config.json uses new menuOrder.
- Extended indicatorVisuals.* with patchable guideGlowUnits, sunGlowUnits, glowOpacity, guideSamples and shared technical ray lengths. New native version 0.2.53, original Android app ID, native camera and automatic Download updater preserved. Installed signed 20036 brightness offset 100dp remains, new signed 20037 overlay patch should carry brightness offset 100dp while unifying older azimuth/elevation values.
- CI/Android physical device verification pending; do not claim release until successful tests/signing. Old .pupdate cannot inject new rendering into native older APK.


### 2026-10-22 — 0.2.53 release verification and local signed 20037 patch
- Source release commit 034e07a18ae1bb50994c6a93afffdc0f238e6d0c. GitHub Actions: Android Build runs 39685122306 / 39685112721 both SUCCESS; Filament Android run 39685112632 SUCCESS.
- Exact original-key APK: `GlassStudy_3D_LAB_v0.2.53_ORIGINAL_KEY.apk`, 11,521,651 bytes, SHA-256 `d5c587f98bd3d00f4eaa6f7277d5ec19a52deed8ad0d98c40f5d64a9aca118af`. Official apksigner v2 verification succeeded, original APK certificate SHA-256 `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc`. 99 ZIP entries byte-identical before/after signing; JNI libs 16 KiB aligned, resources.arsc 4B. Verified compiled classes contain new LightGuideProjection and previous LightTouchSession/Download updater.
- Signed local `GlassStudy_Filament_20037.pupdate`, 3,870 bytes, SHA-256 `76f7f53713f8b9e56e1b104f228393a482c6fc7388953121222840178577a49d`; previous original 20036 and new 20037 RSA/SHA-256 validated against actual original PATCH private key. Changes only `modules/overlay/config.json`. For three modes sets `guideGlowUnits=2`, `sunGlowUnits=7`, `glowOpacity=.8`, `guideSamples=180`. Brings azimuth/elevation eight-ray lengths to brightness default; unifies legacy display colors. Preserves brightness offset exactly **100 dp** and max rotation **90 degrees**. Server feed remains 20031, patch is for local Download.
- Pure Kotlin orthographic math regression `LightGuideProjectionRegression.kt` executed locally with kotlinc: PASS (top-down circle, edge-on line, oblique ellipse, vertical-arc tilt).
- Install the original-key APK 0.2.53 over the previous installation (do not uninstall), put 20037 .pupdate in public Download, then open/reopen app for automatic signed patch discovery/activation. Leave 20036 in Downloads if desired; scanner should select strictly newer 20037. For the version 20037 check use diagnostics installed/frame confirmation. **Real Android 16 rendering still not independently verified**, only CI and native signing/projection tests.

## 2026-10-22 — 0.2.54: shared brightness rays, azimuth seam, elevation labels and physical shadow rays
- User video: /4407.mp4 (13.47s, Android portrait 1280x2772) shows azimuth scrub through 219°,315°,40° on glass tumbler/plane; old indicator interpolates raw 359→0 and appears to jump across the orbit. Fixed in LightIndicatorView with LightDirectionMath.shortestAzimuthDelta, shortest angular distance across 360/0.
- User-requested source/shadow consistency: physically directional rays toward the floor MUST oppose the 3D world source vector. Shared LightDirectionMath.source/incoming now powers both CameraLightController Filament LightManager.setDirection and the azimuth/elevation projected guides. After physical elevation>90°, cos(elevation) changes sign on BOTH source XZ and ray XZ, so the cast shadow still points away from the sun. Tested for azimuths and elevations on both sides of 90°. Note: scene IBL/AO can create nondirectional shading; actual phone shadow behavior still to verify.
- Height text maps continuous physical 0..180° onto user-requested 0° → 90° → -0° with signed return-side values; no underlying light-angle flip.
- All three indicators now compute ray lengths using the same actual brightnessPercent 0..200. Only brightness sun rotates; others stay still.
- Caption backgrounds: four new validated patchable numbers per mode (captionBoxPaddingXUnits, captionBoxPaddingYUnits, captionBoxCornerUnits, captionBoxOpacity), using patch-controlled overlay.backgroundColor with independent caption text and real Android Canvas round rect.
- Version bump to 0.2.54 (20054); all original signed patches remain loadable, previously working 20037 offset 100dp and rotation 90° preserved. Existing updater untouched.
- CI/build/signing/user device checks pending. Regression source and pure Kotlin tests added.

- Follow-up UI correction: user wants *translucent* dark rounded caption plates. Set validated captionBoxOpacity default and bundled overlay for all modes to **0.45** (45% opacity / 55% transparency), still patchable individually. Regression checks opacity remains below 0.55.
