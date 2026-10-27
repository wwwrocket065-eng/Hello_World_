# GlassStudy 3D LAB v0.2.23
Патчируемые настройки интерфейса через ui/layout.json:
- showLogoUnderline=false, logoColor, fillChipCenter=true
- buttonTextColor и uiButtons[].textColor: бирюзовый #6DE0DE
- topButtonSizeDp=35 (30..46), topIconScale=0.82 (0.55..1.0)
- showCameraHint=false
- immersiveMode=true (убраны Android status и nav полосы; временно возвращаются свайпом), systemBarColor если полосы включить
- selectorTextColor, fpsTextColor
- прежние UI кнопки добавлять/удалять/перемещать можно подписанными патчами
- старая конфигурация патча 20024 без новых полей использует новые значения по умолчанию.
Сохранены исправленная камера, Filament, RSA/SHA256 проверка, откат обновления, модель и пакет приложения.
