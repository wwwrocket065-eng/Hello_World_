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


## Checkpoint 2026-10-19 — source 0.2.39
- Added offline real RSA/SHA tests at glassstudy/tools/test_patch_integrity.py and unsigned APK/16K preflight at glassstudy/tools/test_apk_preflight.py, enforced in GitHub Actions. Added PatchManager.stage guard when pending install is unconfirmed. 0.2.39/20039 is an unsigned-source/CI milestone; live patch feed stays 20028.
- Verified original GlassStudy signing backup exists in the user's private Library (GlassStudy_PRIVATE_Keys_Backup.zip), NOT in the public GitHub repository. Original APK certificate SHA256 35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc. Never expose private-key bytes.


## Release checkpoint 2026-10-19 — GlassStudy 0.2.39 ORIGINAL-KEY APK ready
- Native source APK built by GitHub Actions from code commit 62a02426ae3279868c535d711a3f4072b23af2c7, build run 39321129964. The three 0.2.39 workflows (two Android builds and one Filament build) all passed, including real published 20028 RSA/SHA tamper tests and official unsigned-APK package/version/ZIP/16-KiB checks.
- Resulting file in conversation artifact storage: GlassStudy_3D_LAB_v0.2.39_ORIGINAL_KEY.apk, 11,488,883 bytes, SHA-256 dab14762145b9707de53c8288ea79fd2b8456b8f0d18fb9969b360e1ca06025d.
- Original signing source: protected private Library GlassStudy_PRIVATE_Keys_Backup.zip, never copied into GitHub or the signed package. Original DER certificate SHA-256 35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc. Confirmed same certificate fingerprint against protected Library GlassStudy_3D_LAB_v0.2.30_VERIFIED_ORIGINAL_KEY.apk. Private key was extracted only into an ephemeral protected temporary directory and deleted after signing; temporary copy of the key backup in runtime was deleted.
- apksigner verify --min-sdk-version 26 --print-certs --verbose: v2 true; v1/v3 false; signer fingerprint matches original. ZIP payload SHA digests identical pre/post signature; native .so library alignment 16 KiB, resources.arsc 4-byte; valid ZIP CRC. Package ID com.glassstudy.filament.stable, Android versionCode 20039, versionName 0.2.39 checked in CI.
- Published .pupdate feed stays 20028. Runtime installation over user's current Android 16 APK, real touch/lighting rendering, local patch install, crash-recovery and four-frame confirmation are NOT yet phone-verified. Do not mark device test complete from CI alone.
- The next Git commit adds documentation only. It does not alter built APK payload or source and does not imply the signed APK was produced from that later documentation-only commit.


## 2026-10-20 v0.2.40 compact toolbar architecture
- UiChromeController renders VISUAL button backgrounds as inset squares inside unchanged 44dp touch hitboxes; no more overlarge 44dp visible squares. UiLayout gets signed-patchable gearInnerRoundness and LabIconView gear now has eight smooth inner-tooth curves. Default native visual topButtonSizeDp 35dp, patch target 32dp for testing. Build signed native APK v0.2.40 with original key first; older v0.2.39 cannot fully render new geometry or smaller visual backgrounds from a patch alone.


## 2026-10-20 fast modular patch builder checkpoint
- glassstudy/tools/fast_patch.py is the unified no-Gradle/no-APK signed .pupdate builder: --version N --set module.property=jsonValue (repeatable), --file allowed/resource=local_path (repeatable), --dry-run, --key-backup protected original PATCH signing archive, --output directory. All built-in signed module configs and allowlisted resources are supported. It verifies cumulative signed baseline from published patch history first to avoid reset of old customizations.
- CI runs tools/test_fast_patch.py. Protected key backups must never be pushed to repo; publish via existing deploy_feed.py after review. Old live feed 20028 is unchanged. Native Kotlin changes require original-signing APK and cannot be arbitrary patch executables.


## 2026-10-20 server auto-update restored for installed v0.2.40
- Previously server feed was 20028 while user asked to install signed appearance patch 20030 and supplied Android 16 diagnostics showing 20028/20028/20028. Published signed 20030 to the server in commit 98e1c857, no APK change. test_fast_patch.py now computes future test version from latest feed (commit 51e795ea). Fresh Activity onCreate checks the remote feed; diagnostic target after restart is 20030 installed/confirmed/server. Standalone Downloads files are only imported via ACTION_VIEW/ACTION_SEND, NOT automatically scanned under Android 16 scoped storage.


## Source milestone v0.2.41 (2026-10-20)
- UiLayout: new patchable topButtonGapDp/topButtonLeadingDp/bottomButtonGapDp/bottomRowGapDp, validated ranges and legacy defaults. UiToolbarController no longer hardcodes 4dp/8dp margins. UI config and tests updated. One original-key signed 0.2.41 native APK needed before signed patch can control gaps; currently installed 0.2.40 cannot implement code it lacks. Live published patch feed 20030 remains unchanged pending rollout testing.

- Signed topButtonGapDp accepts -12..24dp; modest negative gap enables the old reference layout spacing while leaving visual/icon size and individual button touch targets unchanged. Proposed signed test patch 20031: gap -5dp, leading 0dp. Verify all four click targets individually on device.


## 2026-10-20 — v0.2.42 all-button patch architecture
- Signed UiButtonSpec options: widthDp 44..260, visualSizeDp 20..72, iconScale .55..1.0, offsetXDp/YDp -80..80, marginStartDp/TopDp -16..32. Applied independently to top and bottom buttons. Full built-in uiButtons baseline includes each setting, with safe optional defaults for old signed patches. Next: fast_patch.py --button ID.FIELD=VALUE tool; sign original APK before user-device use. Published server patch remains 20030.

## 2026-10-20 checkpoint 0.2.43
- LocalPatchDirectory.kt implements persisted ACTION_OPEN_DOCUMENT_TREE folder access to user-selected subfolder, query DocumentsContract child docs, choose latest signed .pupdate. MainActivity.checkLocalThenRemote scans on app start and uses EXISTING PatchManager.installFromUri, which checks signature and version. It falls back to GitHub only if no local candidate or local stage fails. Settings has reselectable 'Папка локальных патчей' option. APK must be rebuilt once with original key, then subsequent declarative updates need neither GitHub nor APK. Android 16 user must grant the folder one time; Download root not necessarily selectable.
