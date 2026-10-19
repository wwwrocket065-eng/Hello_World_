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
