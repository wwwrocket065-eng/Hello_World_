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
