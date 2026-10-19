# GlassStudy — RELEASE PLAYBOOK (GitHub Actions, APK, .pupdate)

Репозиторий `wwwrocket065-eng/Hello_World_`, ветка `glassstudy-filament-ci`, проект `glassstudy/`. **Никаких секретных значений в этом документе или GitHub.**

## 1. Найти и изменить исходники GitHub

1. Считать HEAD `refs/heads/glassstudy-filament-ci`, версию из `glassstudy/gradle.properties`, feed `glassstudy/updates/v2/latest.json`.
2. Получить нужные файлы (GitHub `fetch_file` или GitHub REST contents с `ref`); не редактировать вслепую старую копию.
3. Для малой одиночной текстовой правки: `update_file` с текущим SHA файла; для нескольких файлов: `create_tree(base_tree_sha=current tree, tree_elements)` → `create_commit(parent_sha=current HEAD)` → `update_ref(expected_sha=current HEAD)`.
4. После записи повторно fetch HEAD, проверить, что commit/файлы изменились. При конфликте refs заново fetch+rebase, не применять `force` без крайней необходимости.
5. Версия: изменить `GLASSSTUDY_VERSION` и `GLASSSTUDY_VERSION_CODE` в `glassstudy/gradle.properties`; синхронизировать ожидания в `glassstudy/tools/test_project.py`. Не редактировать versionName в UI отдельно.
6. Добавлять причины ошибок и разбор в `glassstudy/DEV_NOTES.md`.

## 2. Собрать и подписать APK без ПК пользователя

- `.github/workflows/glassstudy-build.yml` запускается на push/PR и вручную.
- В CI: Java 17, Gradle 8.10.2, Android SDK 35, Python генерация 66 GLB, download+validate KTX IBL, `python3 glassstudy/tools/test_project.py`, `gradle --no-daemon :app:assembleCiUnsigned`.
- Проверить GitHub Actions runs/jobs. Если fail: сначала читать build log artifact или job logs; фиксировать точную причину, исправлять, повторять.
- CI artifact: unsigned `.apk` внутри `GlassStudy-CI-unsigned-SIGN-BEFORE-INSTALL` (название брать из реально текущего workflow; в старых запусках имя было версионным).
- **Никогда не выдавать unsigned APK как устанавливаемый.** Подписывать исходным сертификатом вне GitHub, через `apksigner` (Android Signature Scheme v2/v3), удостовериться в корректном zipalign и отсутствии подмены ключа.
- Для установки поверх предыдущей сборки: проверить `applicationId=com.glassstudy.filament.stable`, versionCode > предыдущего, SHA-256 certificate fingerprint совпадает с ранее работающей сборкой; публичный отпечаток исходного GlassStudy сертификата из проверенных предыдущих релизов: `35ef51601249bac0ee7fc9fc88a7b380b7750bc9169dde0da382145ff69873cc`. Перепроверять по эталонному APK.
- Приватный ключ/сертификат хранится **только** в защищённом вне-репозиторном хранилище владельца или временном закрытом runtime. При недоступности исходного ключа выпуск устанавливаемого обновления приостановить; не подписывать новым.
- Пример **без секретов**: `apksigner verify --verbose --print-certs release.apk`; следить за versionCode через `aapt dump badging` или `apkanalyzer`. Выдавать sandbox файл только после фактического создания и проверки пути.
- Компиляция+подпись != работа на реальном смартфоне. Попросить скриншот/диагностику и при баге сохранить working APK.

## 3. Выпустить маленький подписанный .pupdate

- Поддерживаемые пути в `PatchManager.kt`: `ui/layout.json` и whitelist `models/<name>_<lod>[_debug].glb`. Патч не выполняет DEX/native code.
- Формат `glassstudy-filament-assets-v2`, `version` должен монотонно расти и быть > установленной на телефоне. Каждая `entry`: относительный `path`, sha256 original bytes, base64 data.
- Канонические подписываемые байты: UTF-8 строка `glassstudy-filament-assets-v2\n<version>\n` + записи `path\tsha256\n` в лексикографическом порядке.
- RSA PKCS#1 v1.5 SHA-256 подпись от ключа, соответствующего публичному ключу, встроенному в `PatchManager.kt`. Исходный защищённый patch signing key **не коммитить**.
- Использовать `glassstudy/tools/publish_patch.py --version N --ui <config.json> --key-backup <SECURE_KEY_BACKUP> --output <DIR>` для UI (проверить фактический usage); или `--models <GLB files>` для моделей. `publish_patch.verify(...)` проверяет содержимое/подпись.
- Удобно сначала создать локально патч и `latest.json`, проверить RSA и хэши; загрузить **сначала сам патч** в `glassstudy/updates/v2/GlassStudy_Filament_N.pupdate`.
- Только после проверки удалённого файла опубликовать `glassstudy/updates/v2/latest.json`:
  `{"format":"glassstudy-filament-feed-v2","patchVersion":N,"patchName":"GlassStudy_Filament_N.pupdate","patchSha256":"<sha256 actual bytes>"}`.
- Дождаться `.github/workflows/glassstudy-patch-audit.yml` **success**. Ошибки SHA/подписи/GLB/схемы исправить до выдачи. Публичный URL feed: `https://raw.githubusercontent.com/wwwrocket065-eng/Hello_World_/glassstudy-filament-ci/glassstudy/updates/v2/latest.json`.
- Приложение проверяет feed при старте, умеет открыть переданный `.pupdate` из Downloads/share, проверяет SHA256+RSA, staging/activation/rollback, подтверждает патч после отрисованного кадра. Не обещать Android auto-install APK без разрешений/корректной архитектуры.
- После patch UI не должен перекрывать существующие функции; важно, что установленная ранее patch-конфигурация имеет приоритет над bundled `layout.json`. При смене APK публиковать новую подписанную UI конфигурацию, иначе телефон может показать старую.

## 4. Проверки релиза

- Число версии в `gradle.properties`, Android manifest, заголовке UI и диагностике совпадает.
- Содержимое модельных и UI ресурсов работает; камера после Filament fix не перезаписывается каждым кадром.
- Патч установлен ≠ патч подтверждён отрисовкой: в диагностике проверять все три значения (installed/last rendered/server).
- Нет возврата «Применить»/«Экспорт сцены», нет лишних Android permissions.
- UI изменения тестировать на реальном Android: нажатие/закрытие попапа, края экрана, иконки, полноценные переключения.
- Краткий отчёт пользователю: версия, сделано, проверено, что НЕ проверено, реальная ссылка на файл.


## 0.2.39 mandatory CI preflight
- Verify test_patch_integrity.py passes with the ACTUAL published .pupdate and embedded public key; all tamper variants must be rejected. If feed hash or signature fails, stop and investigate rather than changing public-key material.
- After assembleCiUnsigned, run test_apk_preflight.py against the actual APK: original package identifier, matching versionName/versionCode, ZIP consistency, 4B resources alignment, 16KiB uncompressed JNI native alignment and expected unsigned apksigner verification failure. Do not release if any gate fails.
- Only then sign with ORIGINAL private key and verify certificate matches the known installed GlassStudy signer. If key unavailable, keep source/CI milestone and report the signing blocker; never use a new release key.
