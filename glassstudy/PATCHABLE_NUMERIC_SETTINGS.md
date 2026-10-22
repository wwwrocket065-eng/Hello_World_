# GlassStudy — числовые настройки через один .pupdate

## Правило для всех модулей

Каждое безопасное пользовательское число (позиция, масштаб, угол, скорость,
цветовой канал, интенсивность, длительность, отступ, размер) должно быть
декларативным и типизированным. Оно хранится в ресурсах, ограничивается
диапазоном на Android и изменяется подписанным .pupdate. Число должно
**реально использоваться в потребителе**, а не просто лежать в JSON.

Все одобренные ресурсы обрабатываются прежними PatchManager,
PatchUiBundle, PatchActivationCoordinator — с RSA/SHA256, атомарной
активацией, подтверждением кадром и откатом. Не добавлять обходные
установщики, дублирование состояния или произвольные исполняемые скрипты.

### Как увидеть текущие доступные числовые пути

```sh
python glassstudy/tools/fast_patch.py --list-numbers
```

Этот список описывает числовые поля сохранённых JSON. Наличие поля само
по себе не подтверждает его применение потребителем; аудировать следует
пару **JSON → Kotlin parser → runtime consumer → тест**.

### Как изменить несколько параметров одним патчем

```sh
python glassstudy/tools/fast_patch.py --version <NEXT_PATCH_VERSION> \
  --set ui.visualNumbers.buttonCornerDp=15 \
  --set ui.topReservedWidthDp=190 \
  --set windows.diagnosticsPopup.reportTextSizeSp=12 \
  --set camera.brightnessPinchDivisorDp=155 \
  --set scene.graySkyRed=105 \
  --set render.exposureAperture=18 \
  --set overlay.indicatorVisuals.brightness.rotationAtMaxDeg=90 \
  --key-backup <PRIVATE_ORIGINAL_PATCH_KEY_BACKUP>
```

Патч должен использовать следующую свободную версию, быть подписан
**оригинальным ключом патчей** и пройти офлайн-тест и реальный тест на Android.
Пример не подразумевает загрузку ключей в публичный репозиторий.

## Проверенные исходным кодом категории (APK 0.2.50)

| Модуль | Параметры, которые умеет прочитать нативный код |
|---|---|
| `ui` | Кнопки, логотип, размеры/отступы, окна, анимации, адаптивная компоновка; `topReservedWidthDp`, `topMinButtonWidthDp`, `visualNumbers.*` |
| `windows` | Общие размеры/поля тем и `diagnosticsPopup.*` — высота, отступы, шрифты |
| `camera` | FOV/near/far, дистанция, чувствительность камеры и света, исходные углы, ограничения наклона, жест яркости и порог фиксации оси |
| `render` | IBL, экспозиция, FXAA, `ambientOcclusionEnabled` |
| `scene` | Размеры пола и плитки, уровни цвета, девять RGB-каналов стандартного Skybox |
| `overlay` | Цвета, размер индикатора, 18 числовых параметров каждой из трёх форм |
| `diagnostics`, `materials` | Флаги/ссылки на материалы; числовая настройка конкретных PBR-материалов пока реализуется через подписанный GLB, не через произвольные shader-скрипты |

## Ещё нужно перенести или подтвердить

- Мелкая геометрия окна выбора цвета, панелей сцены и редактора индикаторов;
  общие элементы должны брать параметры из существующей темы окна.
- Геометрия программных иконок `LabUiIcons.kt`; произвольные контуры
  необходимо ограничивать, чтобы они не становились невидимыми/неактивными.
- Декоративные цвета и некоторые размеры `UiChromeController.kt`, когда
  они пока не связаны с `UiLayout`/темой.
- Дополнительные опции рендера только после проверки публичного API Filament,
  безопасных диапазонов и производительности. Не менять неподдерживаемые
  свойства и не притворяться, что патч внедряет новый рендерер.
- Унаследованные `render.skyRed/skyGreen/skyBlue`: присутствуют в
  конфигурации, но для выбора стандартного Skybox сейчас используются новые
  `scene.*Sky*` RGB-поля; не объявлять старые поля работающими настройками.
- Инженерные константы контроля безопасности, формата, подписи, порогов
  размера патча, протокола Android и самого API рендера **не** являются
  пользовательскими регуляторами и не должны редактироваться патчами.

## Приёмка следующих числовых параметров

1. Добавить ключ в существующий декларативный модуль со значением по умолчанию.
2. Встроить в типизированный валидатор диапазон, проверку finite, а при необходимости
   целочисленность и ограничения между связанными числами.
3. Использовать поле в реальном UI/рендере вместо литерала в Kotlin.
4. Проверить одновременное изменение с числом из другого модуля через
   `fast_patch.py --dry-run`, RSA/SHA целостность, совместимость старых патчей.
5. Если нативный обработчик отсутствовал в прежней версии, однократно
   обновить APK оригинальным сертификатом; после этого менять параметры
   только ресурсными патчами.
6. Не считать весь проект завершённым до полного аудита потребителей и
   испытания подписанных артефактов на Android 16.

Дата аудита: 2026-10-21. Текущий этап: расширенная кросс-модульная
патчируемость, не заявление о 100% охвате.

## 0.2.59 — Physical atmospheric parameter paths
- render.atmosphereExtinction = 0.14 (validated .001..0.6), optical atmospheric attenuation of directional sun
- render.atmosphereHaze = 0.30 (validated 0..1), directional aerosol sky contribution
- render.atmosphereSkyIlluminance = 14000 (validated 0..80000), approximate zenith-sun sky illuminance lux
- render.atmosphereHorizonFraction = .08 (validated 0..0.30), low-sun diffuse fraction
- render.atmosphereShUpdateDegrees = 3 (validated 0.5..12), SH irradiance rebuild cadence on lighting gesture
- scene.defaultLightingMode = atmosphere (allowed vacuum | atmosphere), signed default; user mode preference persists independent of patches
- Vacuum mode uses neither environment IBL nor artificial fill; atmosphere mode analytically rebuilds SH from sun and scales it with brightness. Older studio RGB values are retained as ignored legacy parse fields only.
