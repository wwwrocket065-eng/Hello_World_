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
