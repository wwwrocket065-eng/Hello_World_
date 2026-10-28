# GlassStudy — ОБЯЗАТЕЛЬНЫЕ ПРАВИЛА РАЗРАБОТКИ

> ЧИТАТЬ ПЕРЕД КАЖДОЙ ПРАВКОЙ. Эти требования пользователя имеют приоритет при выборе архитектуры; не считать задачу выполненной без проверки. Применять эти принципы и к остальным программным проектам пользователя.

## Архитектура: настоящее ООП и модульность

- Писать поддерживаемый, понятный Kotlin-код с разделением ответственности, инкапсуляцией и композиционными интерфейсами; ООП применять там, где уместно. Не создавать «франкенштейна» из копий функций, специальных исключений и одноразовых костылей.
- Общие подсистемы: оформление, окна, интерактивные элементы, жесты, анимации, отображение состояния, сцена, материалы, камера, свет, настройки, диагностика, патчи.
- Если две функции похожи, сделать переиспользуемый компонент с декларативной конфигурацией, а не вторую реализацию. MainActivity не должна становиться единственным местом всей бизнес-логики.
- При ДОБАВЛЕНИИ нового элемента сразу продумывать универсальную архитектуру и будущие расширения. Общий движок анимаций должен поддерживать безопасные привязки к параметрам и эффекты перемещения, вращения, масштаба, длины лучей, цвета, прозрачности — применяемые к разным UI-компонентам.
- Отдельно разделять обработку вводных жестов, параметры рендера и исключительно ВИЗУАЛЬНЫЕ индикаторы. Индикаторы не перехватывают касания и не изменяют параметры самостоятельно.

## Патчируемость и обновления

- По возможности все новые функции сразу оснащать настройками через подписанный `.pupdate`: положение/порядок элементов, размеры, цвет, стиль, тексты, анимации, допустимые действия и условия появления.
- Валидировать схемы и диапазоны, гарантировать совместимость с ранее установленными патчами, стадийную проверку и откат. Произвольный Kotlin/native-код через JSON-патч НЕ исполнять.
- Нативные расширения требуют однократного выпуска корректно подписанной APK; НЕ утверждать, что новое поведение можно добавить одним патчем, если в APK нет обработчика.
- APK подписывать только оригинальным сертификатом официальным Android `apksigner`, проверять `apksigner verify` и сравнивать сертификаты; ранее самодельная v2-подпись приводила к отказу Android.
- Перед релизом тесты и CI, не ломать работающие функции, фиксировать причины ошибок и решения в `DEV_NOTES.md`.

## Текущие требования: интерфейс, сцена, анимированные индикаторы

- Приоритет СЕЙЧАС: довести интерфейс, жесты управления светом, визуальные индикаторы, модели и материалы. **НЕ начинать сейчас разработку часового профилировщика.**
- Все окна/кнопки оформлять единообразно. Патчами изменяются заголовки, цвета, позиции, порядок и поведение в пределах реализованных безопасных компонентов.
- Для света: **один палец** — первый преобладающий вектор жеста фиксирует ОДНУ ось до отпускания: вертикально = угол высоты света по дуге 180°, горизонтально = азимут 360°. Отклонения в другом направлении позже не переключают выбранный режим. **Два пальца сведение/разведение** — яркость 0–200% (100% нормальная). Убрать отдельный ползунок яркости.
- Во время настройки показывать только ОДИН подходящий индикатор, другие скрыты. Индикаторы — анимированные, но пассивные (не реагируют на нажатия).
- Яркость: вертикальная линия, полое восьмилучевое солнце перемещается по ней и вращается, лучи удлиняются с ростом яркости; полоса не видна внутри кольца; процент под индикатором.
- Высота: солнце перемещается по проекции вертикальной полуокружности (0–180°) с учётом ориентации камеры и физического азимута света, без собственного вращения, без линии к центру и без тени под солнцем; угол в градусах.
- Азимут: солнце движется по проекции горизонтальной окружности 360°; круг сверху, эллипс под наклоном, почти линия при горизонтальном взгляде. Без луча к центру и без тени под солнцем; подпись градусов.
- Всем трём индикаторам — один технический вид, одна форма неизменяемого круглого восьмилучевого солнца и подсветка. Проецировать ТОЛЬКО направляющие и путь солнца, но никогда не деформировать/сплющивать солнце. Вращение самого солнца — ТОЛЬКО на шкале яркости 0–200% до 90°.
- Шестерёнка «Настройки» больше НЕ показывает редактор стиля, выбор классического/минимального вида, редактор RGB-цвета солнца и пункт «Стиль индикаторов освещения». В настройках оставить управление папкой локальных патчей. Все безопасные цвета, толщины, сияние и геометрию оставить декларативно-патчируемыми и общими для трёх индикаторов.
- Общее поведение жестов/индикаторов и рендер-параметров проектировать с едиными интерфейсами, без трёх независимых анимационных систем.
- Сохранять отдельный Skybox и пол (шахматная или белая увеличенная платформа, либо без пола), в меню «Сцена» Skybox сверху, «Пол» снизу.

## ПОТОМ: расширенный профилировщик

- После исправления интерфейса/моделей разработать записывающий до 60 минут профилировщик производительности: временные графики CPU, реальных доступных GPU-метрик, FPS, кадра avg/P95, RAM, температур (когда доступны), размера APK/данных по компонентам.
- Связывать временные события действий пользователя с метриками для обнаружения узких мест. Профилировать с закрытым окном диагностики, без ненужных персональных данных и с контролируемым расходом памяти/энергии.
- Не выдавать FPS или CPU-время подачи команды за реальную нагрузку GPU; недоступные показатели явно помечать.
- Сохранять краткий и подробный отчёты, копирование результатов.

## Порядок начала работы в любом новом чате

1. Прочитать ЭТОТ ФАЙЛ и `glassstudy/PROJECT_CONTEXT.md`, `glassstudy/DEV_NOTES.md`, `glassstudy/RELEASE_PLAYBOOK.md`.
2. Сверить `gradle.properties`, HEAD ветки, опубликованный `updates/v2/latest.json`, состояние GitHub Actions и последнюю подтверждённую пользователем APK.
3. После значимой правки обновлять `DEV_NOTES.md`, тесты и эту папку правил при новых требованиях.
4. Никогда не помещать пароли, токены, приватные ключи в Git или заметки. При отсутствии подключения честно сообщить, не выдавать подготовленный локально текст за коммит.

## Уточнение: крупные этапы и настройки
- Продолжать крупными, логически завершёнными изменениями модульной архитектуры, а не разрозненными мелкими правками. После этапа указывать оценку её готовности в процентах и отдельно фактически подтверждённые проверки.
- Вместо трёх полосок использовать шестерёнку. Её меню содержит только «Папка локальных патчей»; выбора стиля или цвета солнца больше нет. Остальные отдельные кнопки сохраняются.


## Обязательный стандарт: единые числовые настройки через .pupdate

- Любое **пользовательски значимое** число (x/y/отступы, размер, масштаб, угол, скорость и сглаживание анимации, длительность, толщина, прозрачность, цвет, интенсивность, чувствительность, параметры камеры/света/рендера) по умолчанию проектировать как **валидируемый параметр подписанного ресурсного модуля**, а не как литерал в Kotlin, который требует новой APK при каждом изменении числа.
- Не делать особые `if` или отдельный параметр ради одной правки. Сначала искать общую спецификацию и переиспользовать её для всех однотипных компонентов. Поддерживать вложенные `fast_patch.py --set module.category.item.property=value` и несколько изменений в одном маленьком патче.
- Для каждой группы нужны тип, единица измерения, разумные min/max, дефолт, совместимость со старыми подписанными модулями, тест корректных/некорректных параметров.
- Не раскрывать для патчей криптографические, авторизационные, ресурсные, форматные и архитектурные ограничения безопасности; не обещать изменение нативного кода без однократного обновления APK.
- При добавлении нового числового литерала в коде задавать вопрос: можно ли безопасно настроить это подписанным патчем? Если да — добавлять в существующий типизированный модуль, не дожидаясь отдельной просьбы.

## 2026-10-22 — Правило жестов при настройке света
- После отпускания всех пальцев индикатор текущей настройки **остаётся видимым**; отпускание не скрывает его и не переключает режим.
- Новый отдельный жест в режиме освещения выбирает соответствующий индикатор: горизонтальное движение = азимут, вертикальное = высота, два пальца = яркость.
- Один одиночный тап на месте (без реального движения) **скрывает индикатор**, не меняет активную настройку и не переключает на следующую.
- Подъём одного пальца после щипка не считается отдельным тапом и не может переключить яркость на угол, пока все пальцы не отпустили экран.
- При выходе из режима света или остановке Activity индикатор можно скрыть; системный ACTION_CANCEL не трактовать как преднамеренный тап.
- Сохранять возможность настраивать допустимый порог распознавания тапа одним подписанным числом camera.lightTapSlopDp.

## 2026-10-22 — Солнце, тень и подписи индикаторов (0.2.54)
- Все три солнца имеют лучи, длина которых зависит от ОБЩЕЙ фактической яркости (0–200%); изменения яркости видны при следующем показе любого индикатора. Угол собственного вращения только у яркости.
- Для азимута с 359° на 0° и обратно интерполировать минимальную циклическую разницу, не обгонять окружность/не перескакивать по длинной дуге.
- Шкала высоты визуально 0° → 90° → −0°, где −0° на противоположном горизонте, но физический угол остаётся непрерывным в диапазоне 0..180°; никогда не менять знаки лучей отдельно по UI-ярлыку.
- Единая математическая модель мирового направления НА солнце и противоположных ему лучей Filament обеспечивает тень на противоположной стороне от светила при высоте 0..180°, включая >90°. При zenith=90° горизонтальная тень должна исчезать, после пересечения менять направление плавно.
- Числа градусов и процентов показывать на тёмной полупрозрачной плашке со скруглением; цвет, отступы, скругление и непрозрачность должны быть числовыми параметрами подписанного overlay config (не Kotlin литералами).

## 2026-10-22 — Bottom controls and subtle halo
- Do not render duplicate bottom button "Свет / Камера"; maintain the working top sun button with the safe toggleLight binding. Declarative UI button removal must remain possible with one signed ui/layout.json patch.
- All 3 lighting indicator guide tracks have **no glow** (guideGlowUnits=0). The 8-ray sun glyph has a **soft**, low-alpha, broad warm halo (sunGlowUnits=10, glowOpacity=0.42); no harsh neon bloom.
- These numeric values are signed overlay parameters. Preserve brightness offset=100dp, rotationAtMaxDeg=90 degrees and rounded value chip opacity=0.45, while numeric glyphs remain 100% opaque.

## 2026-10-22 — Sun aura must be visibly soft, not a barely perceptible Paint shadow
- A screenshot of signed 20039 showed that simply changing native Paint.setShadowLayer(glowRadius=10, alpha=0.42) is NOT enough for visible sun illumination on the glass/floor background.
- Reusable 3-mode LightIndicatorView draws explicit alpha-blended RadialGradient BEHIND the eight-ray sun, with additional soft wide stroke along eight rays, then sharp opaque glyph strokes. This is a clear halo, not a hard neon outline or a cast shadow.
- Sun-only signed inputs: sunGlowUnits, glowOpacity. Guide glow stays entirely separate; all three guideGlowUnits=0. Never draw halo on guides. Text chip remains translucent backplate and fully opaque digits.
- An Android APK update is necessary ONCE for the new native shader drawing (v0.2.55), but numerical opacity/radius changes afterward remain signed .pupdate values. Keep bottom light/camera button absent and top sun button present.

## 2026-10-22 — Shared numeric visual glow system
- One GlowEffectConfig and CanvasGlowEngine shared by Canvas UI paths (circles, rays, icons, outlines, buttons) and configurable via signed .pupdate; do NOT add per-indicator duplicated shadow/gradient code.
- Three independently tunable layers primaryStroke (ring), secondaryStroke (rays), ambient (outer haze). Each layer has bounded radiusUnits, opacity, falloff, integer samples. Smoothly fade to alpha=0 at edge; no obvious abrupt circle boundary.
- Keep the primary ring more visibly illuminated than the rays while preserving sharp round glyph shape, separate unlit orbital guide tracks, and old 20039 patch fallback. Any Filament-based 3D glow would use a separate native rendering adapter; don't imply Canvas shader has updated Filament.

## 2026-10-22 mandatory 0% scene darkness and uniform core glow
- 0% physical lighting must extinguish BOTH the directional sun and indirect IBL. The skybox becomes cached black for exact zero only; restore user-selected skybox above zero. UI remains visible so user can brighten again. Keep scene resource release safe and avoid per-frame allocations.
- Render ambient responsiveness can be tuned by validated signed render.globalBrightnessExponent (0.25 to 4.0), with exact zero preserved. Do not silently floor brightness above 0%.
- CanvasGlowEngine.drawCoreFill is a reusable flat-brightness inner disk, separate from primary ring stroke, secondary rays and ambient haze, all using validated signed GlowEffectConfig. All three indicators share this implementation, with no distorted circles or glowing guides.

## 2026-10-22 — Continuous lighting 0..200% (native 0.2.58)
- The sun HUD graphic stays bright and readable at ALL percentages, INCLUDING 0%; it is a control, never physical light. Do not dim the icon in response to scene lighting.
- Physical direct and reflected IBL illumination must smoothly go to true zero with no artificial nonzero floor. Use common LightingResponseCurve.intensity() with separately validated signed curve exponents. At 100%, preserve nominal base intensities, at 50% scene meaningfully dimmer than at 200%.
- CRITICAL 0→1% skybox discontinuity bug: never replace a solid-color skybox with a black one when reaching exactly 0%. For flat backgrounds, update Skybox.setColor continuously using the same brightness factor with signed skyBrightnessExponent and skyBrightnessCeiling. For texture-based studio background, interpolate an Android black dimmer View layered ABOVE SurfaceView and BELOW UI, ignoring touch events; no abrupt zero-only swap. On-screen Android HUD/labels must not be dimmed.
- Keep the actual Filament scene physically unlit at exact 0% (both direct and indirect intensities 0), not merely darken a lit scene. Studio background texture overlay is a visual complement because Skybox.setColor ignores textures.
- Lighting curve numbers remain patchable via camera.directBrightnessExponent, render.globalBrightnessExponent, scene.skyBrightnessExponent, scene.skyBrightnessCeiling. Preserve user-selected skybox, camera/light direction, 20041 uniform sun core, patch compatibility and original app certificate.

## 2026-10-22 — Lighting/material physics baseline
- Never fake physically illuminated materials using fixed studio_ibl.ktx, broad fake studio fill, or hardcoded constant ambient light. Mode vacuum: exactly ZERO environment IBL and no atmospheric absorption; mode atmosphere: sun-dependent solar extinction/color and diffuse clear-sky irradiance computed from physical solar direction. At 0 global brightness direct/sky both zero. Only Android UI/HUD remains legible at 0 (user requirement).
- Physical sun and all projected sun indicators MUST use SAME LightDirectionMath.source(azimuth,elevation). At zenith 90deg transition azimuth projected side flips appropriately; floor cast shadow direction should oppose the sun. No fake horizon-only indicator math.
- Preserve mode choice in preferences; default atmosphere. All numbers in analytic model independently signed-patchable with bounded validators and usable through fast_patch without native rebuilding.
- Current clear-sky Rayleigh/Mie-inspired SH is an approximate diffuse baseline. Do not assert spectrally correct multiple scattering, prefiltered specular sky cubemap, accurate caustics or multibounce transmission. These require separate tests and follow-up implementation, not pretend studio IBL is equivalent.

## 2026-10-26 — Неприкосновенность работающих механизмов
- НИКОГДА не менять автообновление, доступ к «Загрузкам», манифест разрешений, цифровую подпись или проверку/откат патчей при работе над моделью, сеткой, интерфейсом и прочими несвязанными задачами.
- Любое изменение этих систем допустимо только по прямому запросу владельца, с отдельной проверкой и подтверждением работы на Android 16. Сохранить чтение локальных патчей по подтверждённому пользователем сценарию, не навязывать выбор папки, не добавлять новые разрешения без согласования.
- Сборка CI, оригинальный сертификат и фактическая установка на устройстве — разные критерии; не выдавать невыполненную проверку за успешную. Предупреждение Play Protect не обходить.


## 2026-10-27 — Чекпоинты без повторных вопросов о продолжении
- Пользователь явно разрешил продолжать согласованную работу без повторного вопроса «продолжать?». После значимых проверенных этапов сохранять чекпоинт в DEV_NOTES.md и GitHub: коммит, версия, результаты тестов/сборки, статус APK/подписи, ограничения и следующий шаг. Не выдавать незавершённую сборку за готовый выпуск.

## 2026-10-28 — Динамические измерения до оптимизации трассировки
- Рядом с разрешением/SPP показывать время до подтверждённого завершения GPU, не только CPU-подачи команд. Учитывать очередь после изменения сцены; при новом состоянии начинать новый замер, паузу не включать в итог.
- Диагностика записывает нагрузку ВО ВРЕМЕНИ при закрытом окне: этапы и пики, параметры качества/разрешение/SPP, CPU процесса и потока, реальные GPU-времена при безопасной поддержке драйвером. Недоступные GPU-показатели отмечать явно; CPU/wall-время не выдавать за GPU-время или процент загрузки.
- Сначала выдавать измерительный APK и собирать фактическую диагностику телефона; оптимизацию рендера выполнять отдельным следующим этапом. История ограничена по памяти/сроку, настройки валидируются подписанным diagnostics-модулем. Чекпоинты сохранять без повторного вопроса о продолжении.
