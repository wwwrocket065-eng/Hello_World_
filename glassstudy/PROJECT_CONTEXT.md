> **Перед любой работой:** прочитать [PROJECT_RULES/REQUIREMENTS.md](PROJECT_RULES/REQUIREMENTS.md). Это обязательные требования к модульной архитектуре, патчам, UI и текущему порядку задач.\n\n# GlassStudy 3D LAB — PROJECT CONTEXT / факты и незавершённые задачи

Последнее обновление заметок: 2026-10-19. **Это контекст проекта, а не утверждение об успешном запуске текущего APK на телефоне.**

## Идентичность

- Репозиторий: `wwwrocket065-eng/Hello_World_` (публичный); рабочая ветка: `glassstudy-filament-ci`.
- Путь проекта: `glassstudy/`.
- Android `applicationId`: `com.glassstudy.filament.stable`; `namespace`: `com.glassstudy.filament`. Не менять при обновлениях.
- Система: Android Kotlin, Android SDK 35, Java 17, Gradle 8.10.2, Google Filament 1.57.1 (`filament-android`, `gltfio-android`, `filament-utils-android`).
- Конкретная версия всегда определяется из `glassstudy/gradle.properties`. На 2026-10-19 исходники показывали `0.2.26 (20026)`; последний опубликованный ресурсный патч в `updates/v2/latest.json` был `20027`. Версия APK и номер патча — разные последовательности.
- **Проверка на телефоне:** на скриншотах пользователем подтверждены версии до 0.2.25 включительно; в 0.2.26 новый кастомный PopupWindow есть в исходниках, но до подтверждения пользователем нельзя считать его UX проверенным. Во время создания этих заметок более новые GitHub Actions могли ещё выполняться.

## Исходники и важные файлы

- `glassstudy/app/src/main/java/com/glassstudy/filament/MainActivity.kt` — Android UI, камера/жесты, рендер-колбэк, события кнопок, диагностика.
- `glassstudy/app/src/main/java/com/glassstudy/filament/UiLayout.kt` — валидируемая декларативная конфигурация UI и список разрешённых действий/иконок.
- `glassstudy/app/src/main/java/com/glassstudy/filament/LabUiIcons.kt` — программные векторные иконки (чип с заполненным маленьким квадратом, свет, меню, копировать), ромб GlassStudy.
- `glassstudy/app/src/main/java/com/glassstudy/filament/PatchManager.kt` — подписанный patch manager; проверка SHA256 + RSA; staging, activation и rollback.
- `glassstudy/app/src/main/java/com/google/android/filament/utils/GlassStudyModelViewer.kt` — исправленный на базе Filament 1.57.1 ModelViewer; НЕ перезаписывает `lookAt` перед каждым кадром.
- `glassstudy/app/src/main/assets/ui/layout.json` — базовые параметры UI, обновляемые через подписанный `.pupdate` (если предусмотрены Kotlin-кодом).
- `glassstudy/tools/make_models.py` — 66 GLB (11 типов × 3 LOD × обычная/отладочная), совпадение preview и GLB; `test_project.py` — статическая регрессия; `publish_patch.py` — создание/проверка подписанных патчей.
- `glassstudy/app/build.gradle`, `gradle.properties`, AndroidManifest.xml — версии, разрешения, package ID, Android SDK, подпись.
- `glassstudy/updates/v2/latest.json`, `GlassStudy_Filament_N.pupdate` — опубликованный серверный feed и патчи.
- `.github/workflows/glassstudy-build.yml`, `.github/workflows/glassstudy-patch-audit.yml` — CI и проверка патчей.

## Дизайн и функциональность — требования пользователя

- Цветовая и композиционная основа старой GlassStudy v0.1.22: светло-серая сцена, графитовые/тёмно-синие полупрозрачные скруглённые кнопки, спокойная бирюзовая обводка/текст.
- Логотип слева: ярко-бирюзовый ромб, без лишнего подчёркивания, чуть меньше оригинального. Нижнюю точку ромба выровнять по нижней линии подписи «3D LAB». Название `GlassStudy`; текст `3D LAB · v...` сохранить исходным бирюзовым, **не приглушать вместе с кнопками**.
- Четыре небольшие квадратные верхние кнопки, иконки по центру. Порядок: диагностика-чип (внутри заполненный маленький квадрат), свет, сброс камеры, меню. У всех 4 одинаковая обводка, без отдельной подсветки диагностики.
- Низ: выбор модели + качества, кнопки «Свет / Камера» и «Сетка». **Не возвращать «Применить» или «Экспорт сцены».** Выбор модели/LOD применяется немедленно.
- Больше не показывать строку «КАМЕРА: палец...» и затемняющую полосу над UI. Скрытие Android status/nav баров в immersive mode по конфигурации.
- Значок приложения рабочего стола выбран пользователем: второй концепт, бирюзовый ромб + стеклянная форма; хранится как `@drawable/glassstudy_launcher`. Новые иллюстрации/мокапы НЕ генерировать, когда пользователь просит менять настоящее приложение.
- Инвертировать горизонтальное вращение предмета, вертикальное оставить без изменений. Приближение/отдаление и чувствительность поворота перенесены из v0.1.22.
- Приоритет текущих обсуждений — **интерфейс и его патчируемость**, НЕ исправлять стеклянный стакан без нового запроса.

## UI через подписанные патчи

- `ui/layout.json` c `format=glassstudy-ui-v1`, `schema=1`; проверять валидатором `UiLayout.parse` и RSA+SHA256 в `PatchManager`.
- `uiButtons`: добавление/удаление/перенос/оформление экземпляров кнопок с ограниченными `action` и `icon`; нельзя выполнить произвольный код из JSON. Разрешены `diagnostics`, `toggleLight`, `resetCamera`, `menu`, `selectModel`, `selectQuality`, `toggleGrid`, `toggleFps`. Иконки: `chip`, `sun`, `reset`, `menu`, `objects`, `quality`, `grid`, `fps`, `info` и `copy` где фактически реализовано.
- Патчируемые визуальные параметры: `panelOpacity`, `topOverlayOpacity`, `topButtonSizeDp`, `topIconScale`, `topButtonOutlineColor`, `buttonTextColor`, `uiButtons[].textColor`, `logoColor`, `logoSizeDp`, `logoOffsetYDp`, `showLogoUnderline`, `fillChipCenter`, `showCameraHint`, `immersiveMode`, `systemBarColor`, `selectorTextColor`, `fpsTextColor`, `showFps`, `invertHorizontal`, `zoomPower` и диагностические подписи.
- Пользователь хочет более широкую модульную систему: материалы, геометрия, освещение, шейдеры, камера, настройки, диагностика, всё технически возможное через маленькие патчи. **Нельзя обещать произвольный новый Kotlin/native код посредством текущего формата:** это потребует однажды расширить базовый движок безопасными модулями или нового APK. Уже поддерживаемые конфигурации и модели GLB обновляются без APK.
- Сохранять работающие функции, прежние патчи и обратную совместимость; не доверять содержимому неподписанных файлов.

## Диагностика — ПОСЛЕДНЯЯ ЗАДАЧА

Пользователь хочет окно по образцу v0.1.22, **не** стандартный Android `AlertDialog`:
- Полупрозрачная слегка размытая/матовая скруглённая внешняя рамка; тёмно-синее содержимое, моноширинный светло-голубой скроллируемый текст.
- В шапке ТОЛЬКО «Диагностика» (слева) и маленький квадратный значок «копировать» (справа); без нижних «ОК» / «КОПИРОВАТЬ», без длинного заголовка.
- Значок копирования — два наложенных прямоугольника, пользователь прислал референс. Копирование не закрывает окно.
- Тап за пределами окна закрывает; повторный тап по верхнему чипу закрывает; тап по другой кнопке закрывает и немедленно исполняет новую кнопку. Кнопка «Назад» закрывает только окно.
- Только одно открытое всплывающее окно. Не перекрывать верхние иконки самой панелью.
- На дату заметок `MainActivity.kt` уже содержит `PopupWindow` и `diagnosticPopup`, обработчики `setOutsideTouchable`, `setTouchModal(false)`, dismiss-on-other-click, копирование, `onBackPressed`. Это **исходный код**, а не подтверждение поведения на реальном Android; проверить последовательности касаний на телефоне. В CI добавлены строковые тесты этих случаев.

## Причины прошлых ошибок и правила

- Был большой голубой фрагмент стакана при изменениях камеры: официальный Filament `ModelViewer` создавал fallback manipulator при `manipulator=null` и каждый кадр перезаписывал `camera.lookAt`. Исправлено через собственный `GlassStudyModelViewer` с сохранением остального upstream рендера; пользователь подтвердил, что целый стакан виден в v0.2.19. НЕ возвращать этот баг.
- Стеклянный материал: временные GLB патчи 20021/20023 применялись для проверки; стакан иногда белый/фарфоровый. Это отдельная нерешённая задача, не смешивать с UI.
- Кнопка «Применить» и экспорт удалены по просьбе пользователя. Не возвращать.
- В ранних сборках с `REQUEST_INSTALL_PACKAGES` телефон показывал предупреждения Play Protect; эту установку пакетов из приложения удалили. Не добавлять разрешение без согласования.
- При сбоях патча не отключать весь механизм обновлений: показывать причину, stage/rollback, сохранять предыдущую работающую версию, фиксировать в DEV_NOTES.
- Диагностика должна быть компактной и русской: FPS и P95, CPU приложения, GPU если реально доступен (НЕ выдавать FPS за GPU), память PSS/Java/native, модель/GLB, состояние и подтверждение патча, версия один раз.

## Текущая проверка перед выдачей

- Сверить `gradle.properties`, `updates/v2/latest.json`, HEAD и результаты CI.
- `0.2.26` — **исходная** версия на момент записи. Не утверждать, что APK этой версии готов, подписан или проверен пользователем только на основании исходников.
- Последний подтверждённый пользователем диагностический отчёт на скриншоте: v0.2.25, patch 20027 ✓, FPS~60, PSS~336MB. Новую диагностику 0.2.26 ещё нужно проверить на Android.
- Личные ключи НЕ выкладывать на публичный GitHub и не включать в скачиваемые APK/архивы.

Продолжать изменения маленькими итерациями, с короткими отчётами и настоящими проверенными артефактами.


## Checkpoint 2026-10-19 — source v0.2.33 (pending Android/device verification)
- SceneEnvironmentController.kt is responsible for saved floor/Skybox states and Filament scene resources. SceneSettingsPanel.kt builds the Scene+RGB windows against a typed State interface and the shared GlassDialogFactory panel/option methods. MainActivity only creates/wires the two components.
- Preserve signed scene ordering, existing project IDs and last published patch 20028. Existing device releases do not inherit new Kotlin classes via patch alone. Do not distribute an unsigned artifact.


## 2026-10-19 checkpoint v0.2.34 (source)
- DiagnosticsReportService.kt + DiagnosticsPopupController.kt replace embedded diagnostic format and popup in MainActivity. Integration is through DiagnosticsReportState and DiagnosticsPopupStyle, and existing UiInteractionController toggle modes remain authoritative.
- Same controls and Russian labels as prior version. Before distribution: signed original-key APK and user Android 16 test; never publish new Kotlin via .pupdate to legacy APK.


## Checkpoint 2026-10-19 v0.2.35
- CameraLightController.kt: orbit and light gesture state, Filament camera/light projection. RenderEnvironmentController.kt: indirect studio IBL/exposure/FXAA/AO. Activity is composition root and uses their read-only properties for DiagnosticsReportState.
- Requires CI and Android device checks and only original-certificate signing. Shared signed patch feed 20028 must not be changed for old APKs.


## Checkpoint 2026-10-19 — source v0.2.36
- ModelCatalog.kt owns identifiers/names/LOD; ModelMaterialController.kt owns validated model/material GLB resolution, candidate loading and last-good restore. MainActivity delegates loadSelected and saves grid preference only after success. Version 0.2.36/20036.
- Published feed remains 20028. First-party signed GLB material variants remain a module, not shader code injection. CI + device smoke tests required; do not present unsigned CI APK as installable.


## Checkpoint 2026-10-19 — source v0.2.37
- UiChromeController.kt + typed UiChromeViews now own visual application of validated signed UI/window/overlay settings, including toolbar/selector colors, diagnostic popup style, system bars and shared passive indicator. MainActivity builds views once, binds them to UiChromeController and delegates state/status/skin. Gradle version 0.2.37/20037.
- Preserve old patch feed 20028 for pre-native clients, do not distribute CI unsigned APK. Run Android CI and original-cert Android 16 device smoke tests.

- Note for future refactors: the first v0.2.37 CI compile found a malformed nested Kotlin doc-comment in MainActivity; it was fixed at aad69b78. New regression preflight rejects recurrence before Kotlin compilation. Always check logs for the first syntax error; do not patch individual secondary unresolved references blindly.


## Checkpoint 2026-10-19: source 0.2.38
- PatchActivationCoordinator.kt performs ordered signed-patch validate/model/UI/activate and previous-version recovery via existing service host. New PatchManager.activate guard rejects simultaneous nested pending activations; failures preserve detailed phase in diagnostics updateStatus. Gradle version 0.2.38/20038.
- Do not change published patch 20028 without secure signing & audit. Native Kotlin work still requires original-cert signed APK and real Android 16 test.
