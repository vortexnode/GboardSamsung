# GboardSamsung — Samsung One UI 8 Keyboard Appearance: Финальный технический аудит перед реализацией (Audit v3)

**Проект:** [vortexnode/GboardSamsung](https://github.com/vortexnode/GboardSamsung)  
**Базовая ветка / состояние репозитория:** `main` (v4.4.3; проверенная пользователем база v4.4.2)  
**Целевая версия Gboard:** `18.5.4.993608717-release-arm64-v8a` (профиль привязок `gboard-version-bindings.v1`, `profile_version: 1.6.0`)  
**Целевое устройство тестирования:** Samsung Galaxy S22 5G (`SM-S901U1`), Android 16 (API 36), One UI 8, без root, установка через Morphe  
**Статус документа:** Строго исследовательский аудит (реализация, правки кода, патчей, CI, PR, релизы и коммиты **не выполнялись**)  
**Связанные предыдущие отчёты (сохранены без изменений):** `samsung-keyboard-appearance-audit.md` (v1), `samsung-keyboard-appearance-audit-v2.md` / `.pdf` (v2)  
**Планируемые ссылки публикации после утверждения:**  
- Markdown: [samsung-keyboard-appearance-audit-v3.md](https://github.com/vortexnode/GboardSamsung/blob/main/docs/reports/samsung-keyboard-appearance-audit-v3.md)  
- PDF: [samsung-keyboard-appearance-audit-v3.pdf](https://github.com/vortexnode/GboardSamsung/blob/main/docs/reports/samsung-keyboard-appearance-audit-v3.pdf)

---

## Иерархия приоритетов аудита

Все архитектурные решения и оценки рисков в этом отчёте подчинены строгому порядку приоритетов:

1. **Безопасность и стабильность существующей клавиатуры** (ноль крашей, ноль регрессий Direct Boot, ноль сломанных обработчиков ввода).
2. **Сохранение всех функций оригинального Gboard** (свайп-набор, подсказки, буфер обмена, голосовой ввод Rambler, эмодзи, GIF, Action Keys, режимы Floating/Split/One-handed).
3. **Совместимость с существующими патчами GboardSamsung и Morphe** (`Custom Theme`, `Frosted Glass`, `Rounded Keyboard Panel`, `Samsung Spacebar Gestures`, `Access Point Count`, `Access Points Menu`, `Editing Access Points`, `Key Shape Selection`, `Package Rename`).
4. **Корректная работа на целевой версии Gboard `18.5.4`** без опоры на недоказанные допущения от сторонних тем или старых версий.
5. **Визуальное сходство с Samsung Keyboard / One UI 8** — исключительно в рамках безопасных, проверяемых и обратимых механизмов.

---

## 1. Executive Summary

### Краткий вывод

Приблизить внешний вид **GboardSamsung** на `Gboard 18.5.4` (Samsung Galaxy S22 5G, Android 16 / One UI 8, без root) к **Samsung Keyboard One UI 8** при сохранении оригинального движка Gboard, иконок Google, всех обработчиков ввода и всех существующих патчей **технически осуществимо**, но **только поэтапно и с жёстким разделением на подтверждённые и экспериментальные слои**.

Главные корректировки, внесённые в **Audit v3** по итогам проверки исходного кода репозитория и реального скриншота текущей сборки GboardSamsung (`Screenshot_20261121_205256_ChatGPT.jpg`):

1. **Реальная конфигурация верхнего тулбара пользователя отличается от эталона Samsung One UI 8:** в GboardSamsung **нет кнопки `•••`**. В реальном скриншоте пользователя отображаются **9 элементов**: слева кнопка меню Access Points (сетка 4 квадрата), затем 7 настраиваемых инструментов (`Translate`, `Writing Tools`, `Clipboard`, `Select All`, `Paste`, `Text Editing`, `Globe` — всего 8 слотов слева/в центре при настройке `Access Point Count = 8`), а **справа — кнопка голосового ввода Rambler (микрофон с искрой)**, которая **уже имеет собственную нативную круглую подложку** в Gboard `18.5.4`.
2. **Кнопка голосового ввода Rambler архитектурно отделена от обычных Access Points:** в коде GboardSamsung (`GboardRamblerStockPolicy`, `GboardRamblerOfficialSelectorPatch`, `GboardRamblerResetHeader`) Rambler управляется отдельным набором флагов (`enable_agentic_dictation`, `enable_rambler_al_toolbar`, `ad_activation_type = 2L`) и собственным жизненным циклом голосового заголовка (`Lflb;`, `Lfkl;`, `Lfkm;`, `Lflz;`, `Lkaf;`). Её View/Drawable/CSS-селектор **не подтверждены** в исходном коде репозитория и **не могут** считаться идентичными обычным Access Points.
3. **Критическая находка по Direct Boot и хранилищу ZIP (`GboardCustomThemeInstaller.java`):** на данный момент установщик записывает ZIP-архивы тем в **`context.getFilesDir()` (CE-хранилище)**, тогда как предпочтения `additional_keyboard_theme` и `enable_key_border` пишутся в **`context.createDeviceProtectedStorageContext().getSharedPreferences(...)` (DE-хранилище)**. Без прямой проверки загрузчика префикса `"files:"` в Gboard `18.5.4` **запрещено вслепую переносить ZIP-темы в DE-хранилище**, иначе Gboard может перестать находить тему даже после разблокировки.
4. **Шевроны на пробеле (`‹ Русский ›`) — обязательное требование — реализуемы без модификации текста и без перехвата касаний:** оптимальный кандидат — пассивный `Drawable` в `ViewOverlay` поверх `SoftKeyView`, привязанный через уже существующую в `18.5.4` точку `SoftKeyView.s(SoftKeyDef, long)Z` (`soft_key_bind`) и проверку `KeyEvent.KEYCODE_SPACE (62)` через `GboardSpacebarKeyHitTester`, с привязкой координат к внутреннему keycap-слою `.space_bar` и обязательной очисткой при переиспользовании `SoftKeyView`. Требуется проверка внутренней иерархии `SoftKeyView` на устройстве.
5. **Круглые подложки под обычными Access Points при 3–8 элементах — зона повышенного риска:** на экране шириной ~360 dp (Galaxy S22) при `8` элементах + меню + Rambler на один слот приходится всего ~38–40 dp. Массовое добавление круглых подложек через недоказанные CSS-селекторы или runtime-хуки без проверки иерархии `keyboard_header` может дать визуальное слипание, двойную обводку кнопки Rambler или конфликт с `AccessPointDragPopupView` (`GboardAccessPointsMenuRuntime`). Этот элемент вынесен в отдельный условный Этап 5.

### Сводная карта зрелости подсистем

| Подсистема | Базовый механизм | Готовность до теста на S22 | Уровень риска |
| --- | --- | --- | --- |
| Оформление фона и клавиш (Light/Dark) | ZIP/CSS-темы (`metadata.json` + CSS) через `GboardCustomThemeInstaller` | Архитектура валидации/установки подтверждена; рендер CSS в `18.5.4` не проверен | Средний |
| Форма функциональных клавиш (`?123`, `Enter`) | CSS + проверка взаимодействия с `more_pill_keys` (Material 3) | Не подтверждено, переопределяет ли CSS pill-геометрию `18.5.4` | Средний |
| Шевроны пробела (`‹ Русский ›`) | Пассивный `Drawable` в `ViewOverlay` из хука `SoftKeyView.s(...)` | Точка хука и `KEYCODE_SPACE` подтверждены; внутренние bounds `.space_bar` не проверены | Средний |
| Фон и цвета иконок верхнего тулбара | CSS (`.keyboard-header-area`, `.icon.access-point-item` и др.) | Тег `.keyboard-header-area` подтверждён в `Frosted Glass`; CSS-селекторы иконок не проверены в `18.5.4` | Средний |
| Круглые подложки 3–8 Access Points | Только после инспекции `18.5.4` header hierarchy и защиты Rambler | Иерархия слотов и адаптивный диаметр при 8+1 элементах не подтверждены | Высокий |
| Кнопка голосового ввода Rambler | **Не трогать / исключить из модификации** до отдельного исследования | Внутренний View/Drawable в `18.5.4` APK не подтверждён; кнопка уже имеет нативный круг | Высокий |
| Авто-переключение Light/Dark + Backup/Restore | Runtime-наблюдатель в `Lphr;` + DE/CE snapshot настроек | Точки `Lphr;` подтверждены; мгновенная перезагрузка темы без переоткрытия IME не проверена | Средний |

**Итоговый вердикт раздела 1:** **Можно реализовывать после проверки на устройстве**

---

## 2. Что подтверждено исходным кодом

Все факты ниже получены прямым чтением исходного кода репозитория `vortexnode/GboardSamsung` (модули `patches/` и `extensions/extension/`), без предположений.

### 2.1. Установка, валидация и активация ZIP-тем

- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/customtheme/GboardCustomThemeInstaller.java`**:
  - Строки 38, 118, 126: временный файл `.gboard_patches_theme_<uuid>.part`, дедупликация (`user_theme_*`) и финальный `atomicCommit` работают в каталоге **`context.getFilesDir()`**.
  - Строки 43, 76–109: проверка ZIP-безопасности (`GboardThemeImportContract`: лимит числа записей, защита от path traversal, лимит коэффициента сжатия `100x`, лимит распакованного размера, обязательное наличие корневого `metadata.json`).
  - Строки 44, 111–113: валидация архива нативным методом Gboard через `GboardCustomThemeTargetContract.validateThemeArchive(context, staging)`.
  - Строки 63–74: активация темы выполняется записью в **DE-хранилище**:
    - `Context storage = context.createDeviceProtectedStorageContext();`
    - `SharedPreferences preferences = storage.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);`
    - `putString("additional_keyboard_theme", "files:" + committed.getName())`
    - `putBoolean("enable_key_border", keyBorder)`
    - `.commit()` (синхронная запись).
- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/customtheme/GboardCustomThemeTargetContract.java`**:
  - Строки 69–80: вызывает статический метод `boolean` из `GboardCustomThemeVersionBindings.VALIDATOR_CLASS / VALIDATOR_METHOD` (для `18.5.4` это `Lrnw;->h(Ljava/io/File;)Z`).
- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/customtheme/GboardCustomThemeOfficialImportRuntime.java`**:
  - Строки 15, 21–92: перехватывает нажатие «+» в `ThemeListingFragment`, позволяет выбрать фото или ZIP, передаёт имя файла через `intent_extra_key_new_theme_file_name` в `Lkhl;->i(Intent)V`.

### 2.2. Хранение настроек патчей и защита от Direct Boot

- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/GboardPatchesSettings.java`**:
  - Строки 11, 21–28: все настройки GboardSamsung хранятся в файле `gboard_patches_settings` в **CE-хранилище** (`lookupContext.getSharedPreferences("gboard_patches_settings", MODE_PRIVATE)`).
  - Строки 55–89: `preferencesOrNull(context)` и `readRuntimeSetting(...)` **всегда** проверяют `isUserUnlocked(context)` (`UserManager.isUserUnlocked()`), возвращая `null` / `fallback` до разблокировки устройства (fail-closed).
- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/flagsettings/GboardFlagRuntimeContext.java`**:
  - Строки 16–43: получает `Application Context` через `ActivityThread.currentApplication()` / `AppGlobals.getInitialApplication()` и делегирует проверку разблокировки в `GboardPatchesSettings`.

### 2.3. View-теги Gboard, Frosted Glass и Rounded Keyboard Panel

- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/frostedglass/GboardFrostedGlassRuntime.java`**:
  - Строки 47–49, 1140–1155, 1259–1265: подтверждены живые `View.getTag()` строковые теги Gboard `18.5.4`:
    - Поверхности: `.keyboard-base-area`, `.keyboard-base-area.keyboard-outline`, `.keyboard-header-area` (через `contains`), `.keyboard-body-area` (через `contains`), `.candidates-area` (через `startsWith`).
    - Клавиши: `.keytop`, `.keytop.*`, `.space_bar`, `.space_bar.*`.
    - Action Key: `.background-icon.for-action-key` и `.background-icon.for-action-key.*` — причём в строке 1071 прямо задокументировано: *«The action-key fill can be an image separate from its foreground icon»* (`ImageView.getDrawable()` отдельно от `view.getBackground()`!).
    - Исключения: `.for-candidate-key` (пропускается, чтобы не ломать прозрачное неактивное состояние кандидатов).
  - Строки 64, 79–180: жизненный цикл IME перехвачен через `Lphr;` (`onConfigureWindow`, `onWindowShown`, `onWindowHidden`), навигационная полоса на API 36 (`STOCK_STRIP_SDK = 36`) рисуется самим Gboard в `InputView` (ворота `ewx.ak` и `Lrra;->f(Window, Lrqz;)`).
- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/roundedkeyboard/GboardRoundedKeyboardRuntime.java`**:
  - Строки 8–11, 28–49: переопределяет радиусы скругления **только** для двух панельных селекторов: `.keyboard-base-area.keyboard-outline` и `.keyboard-body-area.keyboard-outline-bottom.clip-to-outline-for-old-android`. На радиусы отдельных клавиш этот runtime не влияет.

### 2.4. Пробел, SoftKeyView и жесты пробела

- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/spacebargesture/GboardSpacebarGlobeKeyRuntime.java`**:
  - Строки 16, 34–106: работает через композитор хуков `SoftKeyView.s(SoftKeyDef, long)Z` (`after_stock`, `afterOrder = 900`), отслеживает переиспользованные `SoftKeyView` через `WeakHashMap<View, Integer>` и гарантированно восстанавливает состояние, когда слот переназначается другой клавише.
- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/spacebargesture/GboardSpacebarKeyHitTester.java`**:
  - Строки 21–28, 48–75, 148–187: надёжно определяет клавишу пробела без привязки к layout-ID по метаданным: `SoftKeyView.e` (`SoftKeyDef`) → `SoftKeyDef.g(qae.PRESS)` (`ActionDef`) → `ActionDef.f[0]` (`qbc`) → `qbc.c == KeyEvent.KEYCODE_SPACE (62)`.

### 2.5. Верхний тулбар (Access Points), их количество и меню

- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/accesspointcount/GboardAccessPointCountSettings.java` и `GboardAccessPointCountPolicy.java`**:
  - Диапазон настраиваемого количества элементов: **`MIN_COUNT = 3`**, **`MAX_COUNT = 8`**, **`DEFAULT_COUNT = 6`**.
  - Переопределяются два Long-флага Gboard: `config_max_access_points` и `config_default_access_points_num_on_bar`.
- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/accesspoint/GboardAccessPointContributionsRuntime.java`**:
  - Хукает `Lmvg;->m(...)` (каталог порядка `Lwpa;`) и `Lmwv;-><init>(Context, Lmyc;)V` (контроллер Access Points), синхронизируя `mwv.g` (`Lmwg;`) и `mwg.c` (`Lwpa;`) для `Manual Incognito`, `Floating Web Search` и `Editing Access Points`.
- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/accesspointsmenu/GboardAccessPointsMenuRuntime.java` и `GboardAccessPointsMenuPatch.kt`**:
  - Управляет флагом `enable_access_points_menu_redesign`, переключает body/layout (`LEGACY_BODY = 0x7f0b070c` / `MODERN_BODY = 0x7f0b070d`, `LEGACY_LAYOUT = 0x7f0e062e` / `MODERN_KEYBOARD_LAYOUT = 0x7f0e01aa`) и модифицирует `AccessPointDragPopupView` (`prepareDrag`, `dragMode`, `dragScale`).
- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/featureflags/GboardFeatureFlagsRuntime.java`**:
  - Строки 20–21: `FLAG_KEY_SHAPE_SELECTION = "more_pill_keys"` (включает выбор формы клавиш в Gboard).

### 2.6. Голосовой ввод Rambler

- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/rambler/GboardRamblerStockPolicy.java`**:
  - Строки 10–48: принудительно выставляет флаги `show_rambler_dict_settings`, `filter_rambler_contributed_input_view_session`, `enable_rambler_toolbar_at_cursor_position`, `enable_rambler_al_toolbar`, `ad_activation_type = 2L`, а `enable_agentic_dictation` — условно через `GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation()`.
- **`patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/rambler/GboardRamblerOfficialSelectorPatch.kt` и `GboardRamblerResetHeader.kt`**:
  - Патчит `VoiceSettingsFragment`, `Lnca;->a(Context)Z`, `Lfhv;->hU()V`, `Lflb;->i(Z)V` (с жёсткой проверкой SHA-256 структурных отпечатков `Lflb;`, `Lfkl;`, `Lfkm;`, `Les;` на `18.5.4`) и `Lflz;->J()V` (замена `Lkaf;->e(...)` на `Lkaf;->h(CharSequence)V`, чтобы сброс не захватывал голосовой заголовок повторно).

**Итоговый вердикт раздела 2:** **Можно реализовывать безопасно**

---

## 3. Что подтверждено реальными ZIP/CSS темами

В ходе анализа сторонних ZIP-тем Gboard (из закреплённой ревизии каталога `PackRepoBeta` `4a8187db`: `3D_Black.zip`, `3D_Ios.zip`, `google_blue_dark_port.zip`) подтверждён фактический формат пользовательских ZIP/CSS-тем, применяемых сообществом для Gboard.

> **Критическое ограничение доказательности:** наличие селектора или CSS-свойства в стороннем ZIP-архиве **подтверждает лишь синтаксис формата тем Gboard в прошлых/общих версиях**, но **НЕ доказывает**, что конкретное свойство корректно применяется и визуально рендерится в `Gboard 18.5.4` с Material 3 / Dynamic Color на Android 16.

### 3.1. Структура архива ZIP-темы (сторонние эталоны)

- `metadata.json` в корне архива:
  - `format_version: 3`
  - `id`, `name`
  - `is_light_theme: true | false`
  - `prefer_key_border: true | false`
  - `lock_key_border: true | false`
  - `style_sheets`: массив базовых CSS-файлов (например, `["style_sheet_base.css"]`)
  - `flavors`: условные оверлеи, например `{ "type": "BORDER", "style_sheets": ["style_sheet_border.css"] }`
- CSS-файлы с переменными (`@def color_... #RRGGBB;`) и правилами для селекторов.

### 3.2. Строгая четырёхкатегорийная классификация CSS-свойств и селекторов

В соответствии с требованиями аудита все CSS-свойства и селекторы разделены на **4 непересекающиеся категории**:

| Категория | Свойства / селекторы | Источник / основание классификации |
| --- | --- | --- |
| **1. Реально подтверждены в Gboard `18.5.4`** | View-теги поверхностей и клавиш: `.keyboard-base-area`, `.keyboard-base-area.keyboard-outline`, `.keyboard-header-area`, `.keyboard-body-area`, `.candidates-area`, `.keytop`, `.space_bar`, `.background-icon.for-action-key`, `.for-candidate-key`; панельные радиусы через `Lrnb;->b(String, List)` для `.keyboard-base-area.keyboard-outline` и `.keyboard-body-area.keyboard-outline-bottom.clip-to-outline-for-old-android`; валидатор архивов `Lrnw;->h(File)Z`. | Прямой исходный код `GboardFrostedGlassRuntime.java`, `GboardRoundedKeyboardRuntime.java`, `gboard-version-bindings.json` (`18.5.4`). |
| **2. Найдены только в сторонних темах** | Селекторы: `.keytop.dark`, `.keytop.for-action-key`, `.icon.for-action-key`, `.label`, `.label.secondary`, `.icon.access-point-item`, `.background-icon.access-points-menu`, `.background.access-point-panel-item`, `.keytop:pressed`, `.space_bar:pressed`. Свойства: `background_shape`, `background_corner_radius`, `elevation`, `shadow_color`, `edge_width`, `edge_color`, `padding_ratio_left/right/top/bottom`, `alpha`, `color`, `background_color`, `visibility`, `text_size`. | Сторонние архивы `3D_Black.zip`, `3D_Ios.zip`, `google_blue_dark_port.zip` (`PackRepoBeta` `4a8187db`). Не проверялись на `18.5.4`. |
| **3. Существование подтверждено, но фактическое отображение не проверено** | Применение пользовательских ZIP-тем через `additional_keyboard_theme = "files:<name>"` и `enable_key_border`, а также стилизация существующих в `18.5.4` тегов `.keyboard-base-area`, `.keyboard-header-area`, `.keyboard-body-area`, `.keytop`, `.space_bar`, `.background-icon.for-action-key` через собственный Samsung CSS-архив. | Механизм установки (`GboardCustomThemeInstaller`) и теги подтверждены в `18.5.4`, но ни один Samsung CSS-архив ещё не запускался на `18.5.4`. |
| **4. Требуют эксперимента на устройстве** | (а) Переопределяет ли CSS `background_shape: rectangle` + `background_corner_radius` круглую/pill-форму `?123` и `Enter` при активном Material 3 / `more_pill_keys`; (б) рендерится ли `elevation` / `shadow_color` в `18.5.4` без артефактов; (в) работают ли `.icon.access-point-item`, `.background-icon.access-points-menu` и фоновые подложки тулбара в `18.5.4`; (г) затрагивает ли CSS-селектор иконок тулбара кнопку Rambler; (д) реагирует ли Gboard `18.5.4` на смену `additional_keyboard_theme` мгновенно без переоткрытия клавиатуры; (е) взаимодействие `lock_key_border: true` с пользовательской настройкой «Контуры клавиш». | Невозможно установить статически без прогона тестового ZIP на Samsung Galaxy S22 5G с `Gboard 18.5.4`. |

**Итоговый вердикт раздела 3:** **Можно реализовывать после проверки на устройстве**

---

## 4. Что подтверждено непосредственно для Gboard 18.5.4

Все приведённые ниже факты подтверждены напрямую для `Gboard 18.5.4` (`gboard-version-bindings.json` с `"target_version": "18.5.4"`, `docs/GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md`, структурные SHA-256 проверки в `patches/` и реальный скриншот пользователя `Screenshot_20261121_205256_ChatGPT.jpg`):

1. **Контракт валидации тем `18.5.4`:** `Lrnw;->h(Ljava/io/File;)Z` (`custom_theme_validator`) валидирует ZIP-архивы перед импортом. `ThemeListingFragment` (`Lkhl;`, `Lkhr;`, `Lsrk;`) обрабатывает выбор и импорт ZIP.
2. **Контракт жизненного цикла IME `18.5.4`:** класс `Lphr;` предоставляет точные методы `onConfigureWindow(Window, Z, Z)`, `onWindowShown()`, `onWindowHidden()`, `onStartInput(EditorInfo, Z)`, `onStartInputView(EditorInfo, Z)`, `onFinishInput()`, `onFinishInputView(Z)`, `onDestroy()`, а также поле `Lphr;->i:InputView`.
3. **Контракт привязки клавиш `18.5.4`:** `SoftKeyView.s(SoftKeyDef, long)Z` (`soft_key_bind`, confidence `0.99`), поля `SoftKeyView.e` (`SoftKeyDef`), `SoftKeyDef.g(Lqae;)`, `ActionDef.f`, `Lqbc;->c:I`. Это подтверждённый и уже стабильно работающий в продакшне (`v4.4.2`) путь определения пробела (`KEYCODE_SPACE = 62`).
4. **Контракт навигационной полосы на Android 16 (API 36):** `Lrra;->f(Window, Lrqz;)` (`STOCK_STRIP_SDK = 36`), где `Lrqz;` содержит `a: color`, `b: divider`, `c: OptionalInt body`, `d: OptionalInt header`, `e: boolean light`. На Galaxy S22 5G (Android 16) нижняя полоса под клавиатурой окрашивается самим Gboard внутри `InputView`, а не только через `Window.setNavigationBarColor`.
5. **Контракт верхнего тулбара `18.5.4`:**
   - Контроллер Access Points: `Lmwv;` (`<init>(Context, Lmyc;)`, `k(Lmsx;)V`, `F()V`, `g(Lmtr;, Z)V`), порядок элементов: `Lmvg;->m(...)` и `Lmwg;->c:Lwpa;`.
   - Количество элементов на панели: `config_max_access_points` и `config_default_access_points_num_on_bar` (3–8) через `Loki;->g()Object` (`flag_bool_getter`, confidence `0.99`).
   - Переключение стиля меню Access Points: `enable_access_points_menu_redesign`, ID ресурсов `0x7f0b070c` / `0x7f0b070d`, `0x7f0e062e` / `0x7f0e01aa`, `AccessPointDragPopupView`.
6. **Контракт Rambler `18.5.4`:** жёсткие структурные отпечатки `Lflb;->i(Z)V`, `Lfkl;->d()V`, `Lfkm;->c(Lfkm;)V`, `Les;`, плюс `Lflz;->J()V` → `Lkaf;->h(CharSequence)V` и `Lnca;->a(Context)Z`.
7. **Визуальный факт на реальном скриншоте `GboardSamsung` (`18.5.4` на Galaxy S22):**
   - В верхнем тулбаре **нет `•••`**: слева кнопка меню 4-квадрата (`⊞`), далее 7 иконок инструментов, справа кнопка **Rambler (микрофон с искрой)** в **готовой тёмно-серой круглой подложке**.
   - Клавиши `?123` и `Enter` сейчас имеют **круглую/pill-форму** (Material 3 / `more_pill_keys`), а функциональные клавиши (`Shift`, `Backspace`, `,`, `.`) окрашены в синевато-серый тон Material You.
   - Пробел сейчас отображает центрированную надпись `Русский` без шевронов.
   - Верхние углы панели клавиатуры скруглены (`Rounded Keyboard Panel` / Material 3).
8. **Что НЕ подтверждено для `18.5.4` в репозитории:**
   - В репозитории **отсутствует** проприетарный APK `Gboard 18.5.4`, его `res/raw/style_sheet_*.css` и `res/layout/*header*.xml`. Следовательно, **ни один конкретный CSS-селектор иконок тулбара, ни внутренняя структура View-дерева `SoftKeyView` пробела, ни View-структура кнопки Rambler не могут считаться подтверждёнными до проверки на устройстве или инспекции локальной копии APK**.

**Итоговый вердикт раздела 4:** **Можно реализовывать после проверки на устройстве**

---

## 5. Что требует проверки на устройстве

Ниже приведён закрытый список проверяемых гипотез, которые **невозможно** доказать только по коду репозитория и которые необходимо проверить на Samsung Galaxy S22 5G (`SM-S901U1`, Android 16 / One UI 8, `Gboard 18.5.4`, без root) до перехода к соответствующим этапам реализации:

| # | Что проверить на Galaxy S22 5G | Этап проверки | Критерий успеха | Действие при провале |
| --- | --- | --- | --- | --- |
| **V1** | Проходит ли минимальный Samsung Light/Dark ZIP валидацию `Lrnw;->h(File)Z` и применяются ли `.keyboard-base-area`, `.keyboard-header-area`, `.keytop`, `.keytop.dark`, `.space_bar`, `.label`, `.label.secondary` в `18.5.4`. | Этап 2 | Фоны, буквенные, функциональные клавиши и подписи соответствуют палитре в Light и Dark без крашей. | Корректировка имён селекторов/структуры `metadata.json` по фактическому поведению `18.5.4`. |
| **V2** | Переопределяют ли CSS `background_shape: rectangle` и `background_corner_radius` pill-форму клавиш `?123` и `Enter` при включённом и выключенном `Key Shape Selection` (`more_pill_keys`). | Этап 2 | Предсказуемая форма скруглённых прямоугольников или чётко задокументированная зависимость от `more_pill_keys`. | Не добавлять хрупкий bytecode-хук формы клавиш; задокументировать рекомендацию по `Key Shape Selection`. |
| **V3** | Как отображаются все состояния Action Key (`Enter`, `Search`, `Send`, `Done`, `Next`, `Go`): заливка (`.background-icon.for-action-key` / `.keytop.for-action-key`) и иконка (`.icon.for-action-key`). | Этап 2 | Высокий контраст во всех 6 типах Action Key в Light и Dark, нет «белого на белом» или двойной заливки. | Подобрать контрастный вариант (синяя иконка на функциональной клавише, как на эталоне Samsung One UI 8, или синяя заливка + белая иконка — выбрать тот, что стабильно работает во всех 6 вариантах). |
| **V4** | Какова фактическая иерархия дочерних View внутри `SoftKeyView` пробела (где находится `.space_bar`, где `TextView` метки языка, какие у них `left/right/top/bottom` и `padding`). | Этап 3 | Точно известны внутренние границы видимого keycap пробела и `TextView` языка во всех режимах. | Не рисовать шевроны по внешним краям `SoftKeyView`; привязать к внутреннему `.space_bar` либо использовать выверенный отступ; если границы нестабильны в Split/Floating — отключать шевроны в этих режимах. |
| **V5** | Работают ли CSS-селекторы тулбара (`.keyboard-header-area`, `.icon.access-point-item`, `.background-icon.access-points-menu`) и **затрагивают ли они кнопку Rambler** или полосу подсказок (`.candidates-area`). | Этап 4 | Фон тулбара и цвета иконок корректны, полоса подсказок читаема, кнопка Rambler не искажена. | Сузить CSS только до `.keyboard-header-area` и проверенных безопасных селекторов; не трогать Rambler. |
| **V6** | Какова точная View-иерархия верхнего тулбара в `18.5.4` при 3, 6 и 8 Access Points + кнопка меню слева + Rambler справа: какие `View.getTag()`, ID, классы и размеры (dp) имеет каждый слот. | Этап 5 (пред-гейт) | Подтверждён 100% детерминированный идентификатор типа слота (AP vs Menu vs Rambler vs Suggestions) без опоры на индекс. | **Отказаться от Этапа 5** (круглых подложек Access Points), оставив чистый и безопасный тулбар Этапа 4. |
| **V7** | Перезагружает ли Gboard `18.5.4` тему сразу при записи `additional_keyboard_theme` в DE `SharedPreferences` в `onStartInputView`/`onConfigurationChanged`, или требуется повторное открытие клавиатуры / переинициализация. | Этап 6 | Переключение системного Light/Dark на Galaxy S22 (шторка / расписание) применяет нужную тему без крашей и мерцания. | Зафиксировать фактическое поведение (например, обновление при следующем `onStartInputView` без форсированного перезапуска процесса). |
| **V8** | Поведение Direct Boot: после перезагрузки Galaxy S22 **до первого ввода PIN** (когда CE `getFilesDir()` заблокировано, а DE `additional_keyboard_theme` указывает на `"files:..."`) — падает ли Gboard `18.5.4` или корректно откатывается на стоковую тему? И читает ли `18.5.4` префикс `"files:"` исключительно из CE `getFilesDir()`? | Этап 2 и Этап 6 | Ноль крашей на экране ввода PIN после перезагрузки; после разблокировки Samsung-тема активна. | Сохранить хранение ZIP строго в том же каталоге, что и `GboardCustomThemeInstaller` (`context.getFilesDir()`), не переносить в DE без доказательства. |

**Итоговый вердикт раздела 5:** **Требуется дополнительное исследование**

---

## 6. Архитектура оформления клавиш

### 6.1. Оценка базовой палитры и геометрии относительно Samsung One UI 8

Отправная палитра (проверена по эталонным скриншотам Samsung Keyboard One UI 8 Light/Dark и подлежит тонкой доводке на реальном AMOLED-экране Galaxy S22 в Этапе 2):

| Элемент | Samsung One UI 8 Light (база) | Samsung One UI 8 Dark (база) | Примечание по контрасту и реализации |
| --- | --- | --- | --- |
| Фон клавиатуры (`.keyboard-base-area`, `.keyboard-body-area`) | `#EAEAEF` | `#08080A` | В Dark почти чистый чёрный (AMOLED-совместимый, не #000000 с резкой границей, а глубокий `#08080A`). |
| Фон тулбара (`.keyboard-header-area`) | `#F3F3F7` или `#EAEAEF` | `#121214` или `#08080A` | На эталоне Samsung тулбар в Light чуть светлее тела (`~#F5F5F7`), в Dark чуть приподнят (`~#121214`). |
| Буквенные и цифровые клавиши (`.keytop`) | `#FFFFFF` | `#38383B` | В Light мягкая нижняя тень (`elevation`/`shadow_color` проверить в V1); в Dark плоские высококонтрастные клавиши. |
| Пробел (`.space_bar`) | `#FFFFFF` | `#38383B` | Цвет совпадает с буквенными клавишами, как на эталоне Samsung. |
| Функциональные клавиши (`Shift`, `Backspace`, `?123`, `,`, `.`) | `#C8CACF` | `#1C1C1F` | В Dark функциональные клавиши **темнее** буквенных (ключевое отличие Samsung от стокового Gboard!). |
| Основной текст и иконки клавиш | `#1C1C1E` | `#FFFFFF` | Контраст WCAG AAA (`> 14:1` в Light и `> 11:1` в Dark). |
| Вторичные символы (`.label.secondary`) | `#6B6E75` (или alpha ~0.62) | `#B0B3B8` (или alpha ~0.68) | На скриншоте пользователя вторичные символы включены — важно сохранить их чёткую читаемость. |
| Синий акцент Action Key (`Enter`/`Search`/`Send`/`Done`/`Next`) | `#2A6BF2` | `#4D8BFF` | **Важное наблюдение по эталону Samsung One UI 8:** на эталонных скриншотах Samsung сама клавиша поиска/ввода имеет фон функциональной клавиши (`#C8CACF` / `#1C1C1F`), а **иконка лупы/стрелки окрашена в синий акцент** (`#2A6BF2` / `#4D8BFF`). В Gboard `.background-icon.for-action-key` может рисовать отдельную заливку — в Этапе 2 нужно проверить оба варианта (цветная иконка на функциональной клавише vs синяя заливка с белой иконкой) на всех 6 Action Keys. |
| Состояние нажатия (`:pressed`) | `#DCDCE2` (буквы), `#B4B7BE` (функц.) | `#525256` (буквы), `#2E2E33` (функц.) | Обязательна мгновенная визуальная обратная связь при тапе. |

### 6.2. Принцип: ZIP/CSS-тема как первичный и единственный слой для клавиш

- **Почему НЕ bytecode/runtime-перекраска каждой клавиши:** перехват отрисовки всех ~45 `SoftKeyView` в рантайме создаёт риск лагов при быстром наборе, конфликта с `Frosted Glass` (`applyKeyOpacity`) и поломки popup-подсказок. Нативный движок тем Gboard (`metadata.json` + CSS) — штатный механизм самого приложения, валидируемый `Lrnw;->h(File)Z`.
- **Граница ответственности `Key Shape Selection` (`more_pill_keys`):** на скриншоте пользователя `?123` и `Enter` сейчас круглые (pill). В Этапе 2 необходимо проверить, возвращает ли CSS `background_shape: rectangle` + `background_corner_radius: 7dp` прямоугольную форму при `more_pill_keys = true/false`. **Запрещено** ломать или удалять патч `Key Shape Selection` ради внешнего вида.
- **Граница ответственности `Rounded Keyboard Panel`:** в `18.5.4` радиусы самой панели (`Lrnb;->b`) регулируются отдельным патчем `GboardRoundedKeyboardRuntime`. Тема Samsung не должна конфликтовать с ним: пользователь сохраняет полный контроль над скруглением панели.

### 6.3. Спецификация потенциального изменения (Этап 1: встроенные ZIP/CSS-темы Samsung Light & Dark)

1. **Точное место в исходном коде:**
   - Ресурсы ассетов расширения: новый каталог в `extensions/extension/src/main/assets/` (или `patches/src/main/resources/` с копированием в расширение через сборку `.rve`, аналогично `settings-previews` / `custom-theme-assets`).
   - Установка и валидация через существующий `extensions/extension/src/main/java/dev/jason/gboardpatches/extension/customtheme/GboardCustomThemeInstaller.java` (с отдельным детерминированным именованием, не затирающим пользовательские `user_theme_*.zip` — см. разделы 12–13).
2. **Точный механизм реализации:** два компактных, прошедших `GboardThemeImportContract` и `Lrnw;->h(File)Z` архива (`samsung_oneui8_light.zip` и `samsung_oneui8_dark.zip`) с `format_version: 3`, `is_light_theme`, `prefer_key_border: true` и чистыми CSS-стилями без сторонних растровых картинок (только цвета, радиусы, тени, границы).
3. **Какие файлы потребуется изменить (после утверждения аудита):**
   - Новые ресурсы: `samsung_oneui8_light.zip`, `samsung_oneui8_dark.zip` (или исходники CSS + детерминированная упаковка на этапе сборки Gradle).
   - Новый пакет расширения `extensions/extension/src/main/java/dev/jason/gboardpatches/extension/samsungappearance/` (установщик/активатор поверх `GboardCustomThemeInstaller`).
   - Регистрация в `GboardPatchesSettingsActivity.java` и строках `gboard_settings_text.xml` (RU + EN).
4. **Какие существующие компоненты будут затронуты:** `GboardCustomThemeInstaller`, `GboardCustomThemeTargetContract`, `GboardPatchesSettings`, настройки темы Gboard (`additional_keyboard_theme`, `enable_key_border`).
5. **Какие функции потенциально могут пострадать:** активная пользовательская тема (`Custom Theme`), отображение Action Keys, читаемость вторичных символов и всплывающих подсказок клавиш, совместимость с `Frosted Glass` и `Key Shape Selection`.
6. **Уровень риска:** **Средний** (механизм установки ZIP доказан в `18.5.4`, но фактический рендер всех CSS-свойств в `18.5.4` Material 3 не проверен на устройстве).
7. **Как проверить результат:** установить оба ZIP на Galaxy S22 5G (`18.5.4`), проверить RU/EN/Цифры/Символы/Эмодзи/Буфер/Action Keys/Long-press popup/Swipe-след/Light+Dark/Frosted Glass.
8. **Как откатить изменение:** переключить тему в штатных настройках Gboard («Тема») или выключить режим Samsung Appearance (восстановление сохранённых `additional_keyboard_theme` и `enable_key_border` + удаление наших ZIP-файлов).

**Итоговый вердикт раздела 6:** **Можно реализовывать после проверки на устройстве**

---

## 7. Архитектура шевронов на пробеле (`‹ Русский ›`)

Отображение аккуратных боковых шевронов на клавише пробела (`‹ Русский ›` / `‹ English (US) ›`) — **обязательное визуальное требование** пользователя, напрямую связанное с функцией `Samsung Spacebar Gestures` (переключение языка свайпом влево/вправо по пробелу).

### 7.1. Почему НЕЛЬЗЯ использовать CSS или мутацию текста `TextView`

1. **CSS-тема не может безопасно добавить шевроны:** в CSS-темах Gboard нет подтверждённого в `18.5.4` свойства для симметричной вставки векторных боковых стрелок с динамическим скрытием при узком пробеле, а фоновое изображение `.9.png` на `.space_bar` будет искажаться при изменении ширины (портрет/ландшафт/Split/Floating), конфликтовать с `Frosted Glass` (`applyKeyOpacity`) и плохо адаптироваться к разным радиусам скругления.
2. **Мутация текста `TextView.setText("‹ " + text + " ›")` — антипаттерн (НЕДОПУСТИМО):**
   - Gboard периодически перепривязывает текст языка и может перезаписать или задублировать символы (`‹ ‹ Русский › ›`);
   - искажается `contentDescription` для TalkBack;
   - текстовые символы `‹ ›` (U+2039/U+203A) привязаны к ширине строки текста, а не к левому/правому краю клавиши пробела (на эталоне Samsung One UI 8 шевроны стоят **у краёв клавиши пробела**, а не вплотную к слову `Русский`);
   - меняется расчёт переноса/обрезки длинных названий языков (`Русский • English`).

### 7.2. Рекомендуемый механизм: пассивный `Drawable` в `ViewOverlay` поверх `SoftKeyView`

Наиболее чистая, изолированная и безопасная архитектура в рамках уже существующей инфраструктуры `GboardSamsung`:

- **Точка входа (уже подтверждена в `18.5.4`):** композитор хуков `SoftKeyView.s(SoftKeyDef, long)Z` (`soft_key_bind`, confidence `0.99`), где уже зарегистрирован `GboardSpacebarGlobeKeyRuntime.afterSoftKeyBind(View)` (`afterOrder = 900`). Наш новый обработчик `GboardSpacebarChevronRuntime.afterSoftKeyBind(View)` подключается в тот же `after_stock` композитор (например, `afterOrder = 950`) — **ни одного нового хрупкого bytecode-хука в DEX Gboard не требуется**.
- **Точная идентификация пробела:** переиспользуется проверенный `GboardSpacebarKeyHitTester.extractPressKeycode(softKeyView) == KeyEvent.KEYCODE_SPACE` (62). Никакой привязки к нестабильным ID раскладок!
- **Отрисовка через `ViewOverlay` (`softKeyView.getOverlay()`):**
  - В `ViewOverlay` добавляется лёгкий собственный `SpacebarChevronDrawable` (наследник `Drawable`), рисующий два симметричных векторных `Path` (`<` слева и `>` справа) с закруглёнными концами (`Paint.Cap.ROUND`, `Paint.Join.ROUND`, толщина штриха `~1.6dp`, высота `~8–9dp`).
  - **`ViewOverlay` полностью пассивен для ввода:** он **не участвует** в `dispatchTouchEvent`, `onTouchEvent`, `MotionEvent` hit-testing, `AccessibilityNodeInfo` и layout-перерасчётах (`requestLayout` не вызывается!). Тем самым **гарантируется 100% сохранение**:
    - тапа по пробелу;
    - `Samsung Spacebar Gestures` (свайп влево/вправо);
    - удержания пробела для управления курсором (`FreeCursorMotionEventHandler`, `TriggerFreeCursorMotionEventHandler`);
    - свайп-набора (`ScrubMotionEventHandler`).
- **Жёсткие защитные условия (Fail-Closed & Recycling Safety):**
  1. **Переиспользование `SoftKeyView`:** при каждом вызове `afterSoftKeyBind(view)` если `extractPressKeycode(view) != KEYCODE_SPACE` ИЛИ режим шевронов выключен — `Drawable` **немедленно удаляется** из `view.getOverlay()` и из `WeakHashMap<View, ChevronAttachment>`, а `OnLayoutChangeListener` отписывается.
  2. **Привязка к реальным границам видимого keycap (`.space_bar`):** `SoftKeyView` имеет внешние отступы вокруг клавиши. Внутри `SoftKeyView` нужно найти дочерний View с тегом `.space_bar` (если существует — **проверить в V4 на устройстве!**), иначе взять внутренние границы `SoftKeyView` с учётом его `paddingLeft/Right/Top/Bottom`, и установить `drawable.setBounds(...)` по фактическим координатам через `View.OnLayoutChangeListener`.
  3. **Автоматическое скрытие при узком пробеле и коллизии с текстом:**
     - Если ширина keycap пробела `< 120dp` (например, половинки пробела в **Split Keyboard**, компактный **Floating** или **One-handed** режим, либо узкий пробел при множестве дополнительных клавиш в нижней строке) — шевроны **не рисуются** (`alpha = 0` / skip draw).
     - Если найден дочерний `TextView` с меткой языка, вычисляется фактическая ширина текста (`Paint.measureText`), и если расстояние от края текста до внутренней границы шеврона `< 8dp` — шевроны **автоматически скрываются** во избежание наложения на длинное название языка.
  4. **Адаптация цвета под Light/Dark и тему:** цвет шевронов берётся из текущего цвета текста `TextView` метки пробела (`textView.getCurrentTextColor()` с коэффициентом прозрачности ~0.78–0.85, как на Samsung One UI 8), с фоллбеком на `#1C1C1E` (Light) / `#FFFFFF` (Dark). Благодаря этому шевроны автоматически остаются контрастными даже при `Frosted Glass` или нестандартной теме!
  5. **Поведение при одном языке и RTL:** в RTL-локали (арабский, иврит) визуальная симметрия (`<` слева, `>` справа) сохраняется без зеркалирования. Если активен только один язык ввода (или отключены жесты свайпа по пробелу) — рекомендуется скрывать шевроны (или привязать к настройке «Шевроны на пробеле» с автоматическим скрытием при 1 языке, если число активных субтипов можно безопасно прочитать из `InputMethodManager` без привилегий).

### 7.3. Спецификация потенциального изменения (Этап 3: шевроны пробела)

1. **Точное место в исходном коде:**
   - Существующий композитор `SoftKeyView.s(SoftKeyDef, long)Z` (`patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/shared/` — `SoftKey` family patch / `RuntimeCallId`).
   - Новый класс `extensions/extension/src/main/java/dev/jason/gboardpatches/extension/samsungappearance/GboardSpacebarChevronRuntime.java`.
   - Повторное использование `GboardSpacebarKeyHitTester.extractPressKeycode(View)` (сделать package-private → `public` helper).
2. **Точный механизм реализации:** `after_stock` хук после `SoftKeyView.s(...)` → проверка `KEYCODE_SPACE` и `isUserUnlocked` → прикрепление пассивного `SpacebarChevronDrawable` в `softKeyView.getOverlay()` с `OnLayoutChangeListener`, привязкой к внутренним границам `.space_bar` и защитой от наложения на `TextView`.
3. **Какие файлы потребуется изменить:**
   - `GboardSpacebarKeyHitTester.java` (открыть `public static int extractPressKeycode(View)`).
   - Новый `GboardSpacebarChevronRuntime.java` + unit/Robolectric-тесты.
   - Регистрация нового `RuntimeCallId` в `RuntimeAbiCatalog` и подключение к `SoftKey` family composer.
   - Настройка в `GboardPatchesSettingsActivity.java` и `gboard_settings_text.xml`.
4. **Какие существующие компоненты будут затронуты:** композитор `SoftKeyView.s(...)`, `GboardSpacebarGlobeKeyRuntime` (разные `afterOrder`, не конфликтуют), `GboardSpacebarKeyHitTester`.
5. **Какие функции потенциально могут пострадать:** визуальное отображение пробела при переключении раскладок (`QWERTY` ↔ `?123` ↔ `Emoji` ↔ `Split` ↔ `Floating` ↔ `One-handed`), смена ориентации, читаемость длинных названий языков, анимация/обновление пробела при свайпе языка. Обработка касаний не затрагивается.
6. **Уровень риска:** **Средний** (точка хука и `KEYCODE_SPACE` подтверждены в `18.5.4`, но внутренняя иерархия `SoftKeyView` пробела и поведение `ViewOverlay` при пересоздании/перемасштабировании `SoftKeyView` на `18.5.4` требуют проверки на устройстве).
7. **Как проверить результат:** на Galaxy S22 проверить: RU/EN, 123/символы, свайп пробела влево/вправо, удержание пробела (курсор), быстрый тап пробела, поворот экрана, Split, Floating, One-handed, `Frosted Glass`, включение/выключение тоггла в настройках (мгновенное удаление оверлея без перезапуска).
8. **Как откатить изменение:** выключить тоггл «Шевроны на пробеле» (все `Drawable` немедленно снимаются из `ViewOverlay` и `WeakHashMap` очищается) или откатить коммит добавления `GboardSpacebarChevronRuntime`.

**Итоговый вердикт раздела 7:** **Можно реализовывать после проверки на устройстве**

---

## 8. Подробный анализ верхнего тулбара

### 8.1. Фактическая анатомия верхнего тулбара в GboardSamsung `18.5.4`

По данным исходного кода (`GboardAccessPointCountPolicy`, `GboardAccessPointsMenuRuntime`, `GboardAccessPointContributionsRuntime`, `GboardFrostedGlassRuntime`) и реального скриншота пользователя (`Screenshot_20261121_205256_ChatGPT.jpg`), верхняя полоса (`.keyboard-header-area`) в актуальной конфигурации пользователя состоит из **трёх функционально разных зон**:

1. **Левый фиксированный слот — кнопка меню Access Points (`⊞`, сетка 4 квадрата):**
   - **Кнопки `•••` справа в GboardSamsung НЕТ.** Её аналог в Gboard `18.5.4` — иконка сетки из 4 квадратов на самом левом краю, открывающая панель инструментов (`Lmwv;->F()`, `AccessPointKeyboard`), чей макет зависит от флага `enable_access_points_menu_redesign` (`GboardAccessPointsMenuRuntime`: `LEGACY_BODY = 0x7f0b070c` vs `MODERN_BODY = 0x7f0b070d`).
2. **Средняя настраиваемая зона — обычные Access Points (от 3 до 8 элементов):**
   - Количество задаётся патчем `Access Point Count` (`config_max_access_points`, `config_default_access_points_num_on_bar`, `MIN = 3`, `MAX = 8`, `DEFAULT = 6`).
   - В реальном скриншоте пользователя отображаются 7 инструментов (в сумме с левой кнопкой меню — 8 элементов без подложек): Переводчик, Инструменты письма, Буфер обмена, Выделить всё (`Editing Access Points`), Вставить (`Editing Access Points`), Редактирование текста, Глобус.
   - При редактировании порядка (long-press + drag) задействуется `AccessPointDragPopupView` (`Lmxb;->y`, `GboardAccessPointsMenuRuntime.prepareDrag/completeDrag/rollbackDrag`).
   - Во время ввода текста эта же верхняя зона (`.keyboard-header-area` / `.candidates-area`) динамически переключается на **полосу подсказок / автоисправления / inline-подсказок / буфера обмена / проактивных подсказок**!
3. **Правый фиксированный слот — кнопка голосового ввода Rambler (`🎤✦`):**
   - На скриншоте пользователя **уже имеет встроенную круглую подложку** тёмно-серого цвета и отдельную логику захвата/освобождения заголовка (подробно разобрано в Разделе 9).

### 8.2. Что безопасно менять в тулбаре, а что — зона риска

| Аспект оформления тулбара | Механизм | Безопасность / подтверждённость |
| --- | --- | --- |
| **Цвет фона тулбара** | CSS `.keyboard-header-area` (тег подтверждён в `18.5.4` через `GboardFrostedGlassRuntime.isThemeSurfaceTag`) | **Высокая степень безопасности** после проверки рендера на `18.5.4` (Этап 4). Не трогает обработчики, подсказки и drag-and-drop. |
| **Цвет иконок обычных Access Points и меню** | CSS (кандидаты из сторонних тем: `.icon.access-point-item`, `.background-icon.access-points-menu` — **не подтверждены в `18.5.4`**) | **Средний риск:** требует проверки на устройстве (V5), не ломает ли цвета иконок в активном состоянии (например, открытый буфер/переводчик) и цвет текста подсказок. |
| **Круглые подложки под обычными Access Points** | Потенциально CSS или ограниченный runtime-декоратор | **Высокий риск** (см. Разделы 9 и 10): риск коллизии с Rambler, риск перегрузки при 8 элементах (~38–40 dp/слот), риск поломки drag-and-drop (`AccessPointDragPopupView`), активных состояний (`isSelected`/`isActivated`) и полосы подсказок. **НЕ допускается к реализации без отдельного доказательства безопасности на устройстве (Этап 5).** |

### 8.3. Спецификация потенциального изменения (Этап 4: фон и цвета тулбара)

1. **Точное место в исходном коде:** CSS-файлы внутри `samsung_oneui8_light.zip` и `samsung_oneui8_dark.zip` (без изменения Kotlin/Java-хуков тулбара!).
2. **Точный механизм реализации:** декларативные CSS-правила для `.keyboard-header-area` и проверенных в V5 селекторов иконок/кандидатов.
3. **Какие файлы потребуется изменить:** только CSS-файлы встроенных Samsung ZIP-тем.
4. **Какие существующие компоненты будут затронуты:** рендер `.keyboard-header-area` и `.candidates-area`, взаимодействие с `GboardFrostedGlassRuntime.applySurfaceOpacity` (который также находит `.keyboard-header-area`).
5. **Какие функции потенциально могут пострадать:** читаемость подсказок (Suggestions), контраст активных/неактивных иконок Access Points, контраст иконки меню `⊞`, визуальная согласованность с `Frosted Glass` и `Access Points Menu`.
6. **Уровень риска:** **Средний** (только CSS, но точные селекторы иконок и кандидатов в `18.5.4` требуют эксперимента V5).
7. **Как проверить результат:** проверить в Light и Dark: пустой тулбар, активный набор текста (подсказки + автоисправление), открытый буфер/эмодзи/переводчик, перетаскивание Access Points, включённый `Frosted Glass`, оба режима `Access Points Menu` (Legacy и Modern).
8. **Как откатить изменение:** удалить/скорректировать соответствующие CSS-правила в ZIP-теме и переустановить её (нулевое влияние на байткод).

**Итоговый вердикт раздела 8:** **Можно реализовывать после проверки на устройстве**

---

## 9. Анализ кнопки голосового ввода Rambler

### 9.1. Почему Rambler нельзя приравнивать к обычным Access Points

На основе прямого анализа кода репозитория и реального скриншота пользователя установлены **четыре критических отличия** кнопки Rambler:

1. **Отдельная система feature-флагов и политики (`GboardRamblerStockPolicy.java`):**
   - `enable_agentic_dictation` (контролируется `GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation()` через `Lnca;->a(Context)Z`),
   - `enable_rambler_al_toolbar`,
   - `enable_rambler_toolbar_at_cursor_position`,
   - `filter_rambler_contributed_input_view_session`,
   - `show_rambler_dict_settings`,
   - `ad_activation_type = 2L`.
2. **Отдельный жизненный цикл захвата и освобождения заголовка (`GboardRamblerResetHeader.kt`):**
   - Rambler использует собственную цепочку классов `Lflb;` / `Lfkl;` / `Lfkm;` / `Les;` / `Lflz;` / `Lkaf;`, вплоть до того, что в `GboardRamblerResetHeader.kt` пришлось зашивать **5 строгих SHA-256 структурных отпечатков методов `18.5.4`** и менять вызов `Lkaf;->e(...)` на `Lkaf;->h(...)`, чтобы при сбросе не происходил повторный захват голосового заголовка.
3. **Уже существующая нативная круглая подложка на скриншоте пользователя:**
   - На `Screenshot_20261121_205256_ChatGPT.jpg` кнопка Rambler (`🎤✦`) в правом углу тулбара **уже заключена в собственный круглый/pill-контейнер** с повышенным визуальным акцентом, тогда как 8 элементов левее её не имеют круглой подложки.
   - Во время диктовки этот элемент трансформируется в активную голосовую панель / индикатор прослушивания (wave/pulse, кнопка остановки/сброса, toolbar-at-cursor).
4. **Неизвестный внутренний View/CSS-контракт в APK `18.5.4`:**
   - В исходном коде репозитория **нет** ни ресурсного ID, ни View-тега, ни CSS-селектора этой кнопки в `Gboard 18.5.4`. Делать предположение, что она использует `.icon.access-point-item` или `.background-icon.*`, **запрещено**.

### 9.2. Архитектурное правило для кнопки Rambler

- **На Этапах 1–4:** кнопка Rambler **не модифицируется вообще**. В V5 проверяется, не затрагивают ли общие CSS-правила тулбара (`.keyboard-header-area` и иконки) контраст или фон кнопки Rambler. Если общий CSS случайно искажает Rambler — правило немедленно сужается или удаляется.
- **На Этапе 5 (если эксперимент с круглыми подложками Access Points состоится):**
  - Валидация каждого View-слота должна происходить **строго по типу/метаданным/тегу элемента**, а **никогда не по индексу в `ViewGroup`** (например, «все кроме последнего»).
  - Кнопка Rambler (и любая кнопка голосового ввода) должна быть **явно исключена** из добавления дополнительных круглых подложек: у неё уже есть нативный круг, и повторная обёртка приведёт к «кругу внутри круга», обрезке анимации диктовки или поломке `Lflb`/`Lkaf`.
  - Если на устройстве не удастся со 100% точностью отличить обычные Access Points от слота Rambler — **Этап 5 отменяется целиком**.

### 9.3. Спецификация потенциального изменения (защита Rambler)

1. **Точное место в исходном коде:** `GboardRamblerOfficialSelectorPatch.kt`, `GboardRamblerResetHeader.kt`, `GboardRamblerStockPolicy.java` — **не изменяются**; ограничения применяются в CSS-теме (Этап 4) и в фильтре элементов возможного Этапа 5.
2. **Точный механизм реализации:** явное исключение (allowlist только подтверждённых обычных Access Points) вместо слепого обхода всех детей заголовка.
3. **Какие файлы потребуется изменить:** ни одного файла в пакете `rambler/`.
4. **Какие существующие компоненты будут затронуты:** косвенно — рендер правого слота `.keyboard-header-area` при применении CSS-темы.
5. **Какие функции потенциально могут пострадать:** запуск и остановка диктовки Rambler, переключение на стоковый голосовой ввод в `VoiceSettingsFragment`, анимация активного микрофона, сброс голосового заголовка (`Lflz;->J()`), `Manual Incognito` (где голосовой ввод может скрываться через `Lvec;->aQ`).
6. **Уровень риска:** **Высокий** (при любой прямой модификации Rambler) / **Средний** (при политике «не трогать Rambler + проверка V5»).
7. **Как проверить результат:** в Light и Dark на Galaxy S22 запустить и остановить диктовку Rambler 5+ раз подряд, проверить переход из режима диктовки обратно в тулбар, проверить режим инкогнито (`Manual Incognito`), проверить переключение официального выбора голосового ввода в настройках Gboard.
8. **Как откатить изменение:** откат CSS-правила тулбара / отключение Этапа 5; байткод `rambler/` остаётся полностью нетронутым.

**Итоговый вердикт раздела 9:** **Не рекомендуется реализовывать** *(модификацию самой кнопки Rambler; допустимо только безопасное сосуществование без её изменения после проверки V5).*

---

## 10. Адаптивность тулбара от 3 до 8 элементов

Патч `Access Point Count` (`GboardAccessPointCountSettings.java`) позволяет пользователю выбирать любое число от **`3`** до **`8`** (`config_max_access_points` / `config_default_access_points_num_on_bar`). Кроме того, в тулбаре присутствуют:
- левая кнопка меню Access Points (`⊞`);
- правая кнопка голосового ввода Rambler (`🎤✦`) (которая может также скрываться в полях паролей или при настройке голосового ввода).

### 10.1. Расчёт плотности слотов на Galaxy S22 5G (~360 dp портрет, ~780 dp ландшафт)

Для оценки геометрического риска приведем расчёт доступной ширины на один элемент в портретной ориентации (~360 dp, минус ~8–12 dp боковых полей = ~348 dp рабочей ширины):

| Значение `Access Point Count` | Ориентировочное число видимых слотов (включая меню и Rambler)* | Ширина на слот (портрет ~348 dp) | Оценка безопасности круглых подложек |
| --- | --- | --- | --- |
| **3** | ~4–5 слотов | ~70–87 dp | Геометрически просторно (круг `34–36 dp`, зазор `> 30 dp`). |
| **4** | ~5–6 слотов | ~58–70 dp | Комфортно (круг `34 dp`, зазор `~24 dp`). |
| **5** | ~6–7 слотов | ~50–58 dp | Приемлемо (круг `32–34 dp`, зазор `~16–24 dp`). |
| **6** (по умолчанию) | ~7–8 слотов | ~43–50 dp | Впритык (круг `30–32 dp`, зазор `~11–18 dp`). |
| **7** | ~8–9 слотов | ~39–43 dp | Плотно: круг `> 32 dp` визуально давит на соседние слоты и Rambler. |
| **8** (реальная конфигурация пользователя!) | **9 слотов** (подтверждено на скриншоте пользователя) | **~38–39 dp** | **Критическая плотность:** нативный круг Rambler уже занимает почти всю ширину слота; добавление кругов `34–36 dp` на все 8 левых иконок оставит зазор `2–4 dp` (визуальное «слипание»), а уменьшение кругов до `28 dp` сделает их диспропорциональными относительно круга Rambler и самой иконки (`22–24 dp`). |

*(Примечание: точное соотношение между значением `config_max_access_points` и общим числом видимых слотов — включает ли `8` кнопку меню `⊞` или нет — уже видно на скриншоте пользователя: 8 иконок слева/в центре + 1 Rambler справа = 9 элементов в строке.)*

### 10.2. Что происходит в нестандартных режимах ширины

- **One-handed mode (режим одной рукой):** ширина клавиатуры сжимается примерно на 15–20% (~290–305 dp). При 8+1 элементах ширина слота падает до **~32–34 dp** — любые фиксированные круглые подложки `34–36 dp` будут **обрезаны (`clipChildren`) или начнут перекрывать друг друга**!
- **Floating keyboard (плавающая клавиатура):** ширина произвольно масштабируется пользователем (часто ~280–320 dp) — та же проблема.
- **Split keyboard (разделённая клавиатура):** верхняя полоса перестраивается под широкий экран/разделённый макет (`Lmwg;->c()`).
- **Полоса подсказок (Suggestions / Candidates):** при вводе слова контейнер заголовка скрывает обычные Access Points и показывает подсказки; если runtime-декоратор ошибочно привязался к контейнеру или переиспользуемому слоту, подложки могут «протечь» на кандидатов.

### 10.3. Вывод и строгие критерии допуска Этапа 5

1. **Никаких фиксированных диаметров и никаких привязок к индексам (`0..N-1`).**
2. **Адаптивное правило (если Этап 5 вообще признается безопасным после V6):**
   - Диаметр круга вычисляется динамически от фактического размера каждого конкретного слота: `d = min(slotWidth - 6dp, slotHeight - 6dp, 36dp)`.
   - Если `d < 28dp` (например, One-handed / узкий Floating при 8 элементах) — круглые подложки **автоматически отключаются** (graceful degradation), оставляя чистые иконки без обрезки.
   - При активном перетаскивании (`AccessPointDragPopupView`), скрытом состоянии или показе подсказок подложки не вмешиваются.
3. **Приоритет стабильности:** если проверка V6 покажет, что в `18.5.4` иерархия тулбара при 8 элементах не даёт чистого и безопасного способа отличить обычные Access Points от Rambler и подсказок — **отказ от круглых подложек (Этап 5) является штатным и предпочтительным решением**, а тулбар оформляется чисто через фон и контрастные цвета иконок (Этап 4).

### 10.4. Спецификация потенциального изменения (Этап 5 — условный эксперимент)

1. **Точное место в исходном коде:** пока не определено до проверки V6; потенциально CSS (если `18.5.4` поддерживает отдельный селектор подложки только для обычных AP) либо легковесный `Drawable` в наблюдателе заголовка (по аналогии с `GboardFrostedGlassRuntime`, но строго по allowlist-тегам).
2. **Точный механизм реализации:** динамический адаптивный круг (`GradientDrawable` / `OvalShape`) только на подтверждённых View-слотах обычных Access Points, с автоотключением при тесной ширине (`< 34dp/слот`) и жёстким исключением Rambler и `.candidates-area`.
3. **Какие файлы потребуется изменить:** либо только CSS-темы (если CSS окажется достаточным), либо новый изолированный класс `GboardToolbarBadgeRuntime.java` + настройка-тоггл (выключен по умолчанию до подтверждения на устройстве).
4. **Какие существующие компоненты будут затронуты:** `.keyboard-header-area`, `AccessPointCount` (3–8), `AccessPointsMenu` (Legacy/Modern и `AccessPointDragPopupView`), `EditingAccessPoints`, `FrostedGlass`, `Rambler`.
5. **Какие функции потенциально могут пострадать:** кликабельность и drag-and-drop Access Points, отображение активного/выбранного состояния кнопок, отображение Rambler, читаемость подсказок, One-handed и Floating режимы.
6. **Уровень риска:** **Высокий** (неизвестная иерархия слотов `18.5.4`, экстремальная плотность при 8+1 элементах и One-handed, соседство с Rambler).
7. **Как проверить результат:** протестировать все значения `Access Point Count` = `3, 4, 6, 8` в портрете, ландшафте, One-handed, Floating, Split; проверить перетаскивание иконок, переход в подсказки и обратно, запуск Rambler, включённый `Frosted Glass`.
8. **Как откатить изменение:** отдельный независимый выключатель «Круглые подложки панели инструментов» (мгновенно снимает декоратор без влияния на тему клавиш и шевроны) либо полный отказ от реализации Этапа 5.

**Итоговый вердикт раздела 10:** **Требуется дополнительное исследование**

---

## 11. Архитектура автоматического Light/Dark

### 11.1. Как определять системную тему на Android 16 / One UI 8

- Единственный стандартный, публичный и безопасный источник истины на Android 16:
  ```java
  int nightMask = context.getResources().getConfiguration().uiMode
          & Configuration.UI_MODE_NIGHT_MASK;
  boolean isDark = (nightMask == Configuration.UI_MODE_NIGHT_YES);
  ```
- **Где слушать изменения (уже подтверждённые точки `18.5.4` в `gboard-version-bindings.json`):**
  - `Lphr;->onStartInputView(EditorInfo, Z)` (`calculator_input_start` / `editing_ap_onstartinputview` / `manual_incognito_start_view`)
  - `Lphr;->onWindowShown()` (`frosted_shown`)
  - `Lphr;->onConfigureWindow(Window, Z, Z)` (`frosted_configure`)
  - Опционально: `InputMethodService.onConfigurationChanged(Configuration)` (если потребуется мгновенная реакция при открытой клавиатуре — нужно проверить наличие и привязку в `18.5.4`).

### 11.2. Режимы работы настройки (UX)

В настройках GboardSamsung (`Внешний вид Samsung` / `Samsung Appearance`):
- **Выключено** (по умолчанию — стоковое поведение / текущая тема пользователя).
- **Авто (как в системе)** — автоматически выбирает `samsung_oneui8_light.zip` при `UI_MODE_NIGHT_NO` и `samsung_oneui8_dark.zip` при `UI_MODE_NIGHT_YES`.
- **Всегда светлая (One UI 8 Light)** — фиксирует светлую тему Samsung.
- **Всегда тёмная (One UI 8 Dark)** — фиксирует тёмную тему Samsung.

### 11.3. Защита от гонок, циклов и перезаписи ручного выбора пользователя

1. **Direct Boot guard:** перед любым чтением `gboard_patches_settings` или обращением к `context.getFilesDir()` обязательна проверка `GboardPatchesSettings.isUserUnlocked(context)`. До разблокировки код делает немедленный `return` (no-op).
2. **Идемпотентность:** если `additional_keyboard_theme` в DE `SharedPreferences` уже равен целевому `"files:<target_samsung_zip>"` и `enable_key_border` уже совпадает — **никакой записи в `SharedPreferences` не выполняется** (ноль лишнего I/O и ноль циклов перезагрузки темы).
3. **Защита от перезаписи новой Custom Theme пользователя (Ownership Check):**
   - Мы храним имена установленных нами файлов (`samsung_light_file`, `samsung_dark_file`) в `gboard_patches_settings`.
   - При очередном `onStartInputView`, если текущее значение `additional_keyboard_theme` **не является** ни нашим Light ZIP, ни нашим Dark ZIP (значит, пользователь сам зашёл в Gboard → «Тема» и выбрал другую тему!), авто-переключатель **НЕ перезаписывает** выбор пользователя, а аккуратно переводит режим Samsung Appearance в «Выключено» (или «Приостановлено»), уважая явное действие пользователя!
4. **Вопрос мгновенной перерисовки в `18.5.4` (требует проверки V7):**
   - Изменение DE `SharedPreferences` (`additional_keyboard_theme`) обновляет хранилище, из которого Gboard читает активную тему. Однако в репозитории **не подтверждено**, подписывается ли Gboard `18.5.4` на `OnSharedPreferenceChangeListener` для мгновенной перестройки уже открытого `InputView`, или тема подхватывается при следующем `onStartInputView`.
   - **Запрещено** убивать процесс IME (`System.exit`, `killProcess`) ради мгновенной смены темы — это разрушает текущую сессию ввода и противоречит приоритету #1 (стабильность).

### 11.4. Спецификация потенциального изменения (Этап 6: авто-переключение Light/Dark)

1. **Точное место в исходном коде:**
   - Существующие перехваты `Lphr;->onStartInputView` / `onWindowShown` (через уже существующий lifecycle-композитор `Lphr;` из `gboard-version-bindings.json`).
   - Новый класс `extensions/extension/src/main/java/dev/jason/gboardpatches/extension/samsungappearance/GboardSamsungAppearanceRuntime.java`.
2. **Точный механизм реализации:** проверка `isUserUnlocked` → чтение режима из `gboard_patches_settings` → чтение `Configuration.UI_MODE_NIGHT_MASK` → проверка ownership текущего `additional_keyboard_theme` → идемпотентная установка/переключение на предвалидированный `samsung_oneui8_light.zip` или `samsung_oneui8_dark.zip` через DE `SharedPreferences`.
3. **Какие файлы потребуется изменить:** `GboardSamsungAppearanceRuntime.java`, `GboardSamsungAppearanceSettings.java`, регистрация вызова в существующем `Lphr;` lifecycle-патче, экран настроек в `GboardPatchesSettingsActivity.java`, локализация RU/EN.
4. **Какие существующие компоненты будут затронуты:** `Lphr;` lifecycle hooks (`Frosted Glass`, `Manual Incognito`, `Editing Access Points`, `Calculator`), `GboardCustomThemeInstaller`, DE `SharedPreferences` (`<pkg>_preferences`).
5. **Какие функции потенциально могут пострадать:** время открытия клавиатуры (`onStartInputView` должен оставаться O(1) и не делать дисковых копирований на горячем пути — ZIP-файлы должны устанавливаться заранее, при включении настройки в Settings UI!), `Custom Theme`, `Frosted Glass` (перерасчёт альфы поверхностей после смены темы).
6. **Уровень риска:** **Средний** (lifecycle-точки подтверждены в `18.5.4`, но реакция Gboard на смену `additional_keyboard_theme` «на лету» и взаимодействие с `Frosted Glass` при смене Light↔Dark требуют проверки V7 на устройстве).
7. **Как проверить результат:** переключить тёмную тему Android в шторке Samsung One UI 8 при закрытой и при открытой клавиатуре; проверить переключение по расписанию; проверить ручной выбор другой темы в Gboard → «Тема» (не должна перезаписаться); проверить задержку открытия клавиатуры.
8. **Как откатить изменение:** переключить режим в «Выключено» (восстановит исходную тему) или удалить вызов из `Lphr;` lifecycle-композитора.

**Итоговый вердикт раздела 11:** **Можно реализовывать после проверки на устройстве**

---

## 12. Сохранение и восстановление настроек

Пользователь может включать Samsung Appearance поверх:
- стоковой темы Dynamic Color (когда ключа `additional_keyboard_theme` **вообще нет** в `<pkg>_preferences`!);
- встроенной темы Gboard (`assets:...` или иной строковый идентификатор);
- пользовательской ZIP-темы (`Custom Theme`, `"files:user_theme_1730000000000.zip"`);
- пользовательской фото-темы, созданной штатной кнопкой «+» Gboard;
- любого состояния переключателя «Контуры клавиш» (`enable_key_border` = `true`, `false` или ключ отсутствует).

### 12.1. Точная схема Snapshot / Restore (без потери данных)

При **первом** переходе Samsung Appearance из `OFF` в любой активный режим (`AUTO` / `LIGHT` / `DARK`) — **и строго до** записи нашей темы в DE `SharedPreferences` — в `gboard_patches_settings` сохраняется полный снимок:

| Ключ в `gboard_patches_settings` | Тип | Назначение |
| --- | --- | --- |
| `pref_samsung_appearance_backup_valid` | `boolean` | Флаг, что исходный снимок сохранён и ещё не восстановлен (защищает от перезаписи бэкапа нашей же Samsung-темой при повторном переключении Light↔Dark!). |
| `pref_samsung_appearance_prev_theme_present` | `boolean` | Был ли ключ `additional_keyboard_theme` физически присутствует в `<pkg>_preferences` (`prefs.contains(...)`). |
| `pref_samsung_appearance_prev_theme_value` | `String` | Точное прежнее строковое значение `additional_keyboard_theme` (если `present == true`). |
| `pref_samsung_appearance_prev_border_present` | `boolean` | Присутствовал ли ключ `enable_key_border` (`prefs.contains(...)`). |
| `pref_samsung_appearance_prev_border_value` | `boolean` | Прежнее значение `enable_key_border` (если `present == true`). |
| `pref_samsung_appearance_owned_light_file` | `String` | Имя нашего установленного Light ZIP (например, `user_theme_gboardsamsung_oneui8_light_v1.zip`). |
| `pref_samsung_appearance_owned_dark_file` | `String` | Имя нашего установленного Dark ZIP (например, `user_theme_gboardsamsung_oneui8_dark_v1.zip`). |

### 12.2. Правила безопасного восстановления при выключении режима

1. **Проверка перед восстановлением:** если пользователь уже сам сменил тему в Gboard на другую (текущее значение `additional_keyboard_theme` не равно ни `files:<owned_light>`, ни `files:<owned_dark>`), то при выключении режима Samsung **не откатываем** тему поверх нового ручного выбора пользователя — только сбрасываем `backup_valid = false`.
2. **Точное восстановление `additional_keyboard_theme`:**
   - Если `prev_theme_present == false`: вызываем `editor.remove("additional_keyboard_theme")` (НЕ `putString(..., "")`, так как пустая строка может отличаться от отсутствия ключа!).
   - Если `prev_theme_present == true`: проверяем, если значение начинается с `"files:"`, существует ли исходный файл в `getFilesDir()`. Если файл существует (или это не `"files:"`) — восстанавливаем `putString("additional_keyboard_theme", prev_theme_value)`. Если пользователь успел удалить старый файл — безопасно `remove("additional_keyboard_theme")` (возврат к стоку без битой ссылки!).
3. **Точное восстановление `enable_key_border`:**
   - Если `prev_border_present == false` → `editor.remove("enable_key_border")`.
   - Если `prev_border_present == true` → `editor.putBoolean("enable_key_border", prev_border_value)`.
4. **Неприкосновенность пользовательских ZIP:** код Samsung Appearance **никогда не удаляет и не перезаписывает** файлы, не совпадающие с `owned_light_file` / `owned_dark_file`. Также `findDuplicate` в `GboardCustomThemeInstaller` должен быть учтён: наши встроенные архивы лучше хранить под собственным префиксом или фиксированными именами, чтобы никогда не «присвоить» пользовательский файл как owned.
5. **Интеграция с `Backup & Restore` (резервное копирование настроек GboardSamsung):** при импорте бэкапа настроек на другом устройстве или после переустановки ключи `backup_valid` и `owned_*_file` должны переустанавливаться/перепроверяться, чтобы не оставить ссылку на несуществующий ZIP.

### 12.3. Спецификация потенциального изменения (Backup/Restore настроек темы)

1. **Точное место в исходном коде:** `GboardSamsungAppearanceSettings.java` (новый класс), использующий `GboardPatchesSettings.java` (CE) и `context.createDeviceProtectedStorageContext().getSharedPreferences(pkg + "_preferences", MODE_PRIVATE)` (DE).
2. **Точный механизм реализации:** атомарная запись (`commit()`) снимка перед первой активацией + безопасное восстановление с проверкой `contains()`, существования файла и ownership.
3. **Какие файлы потребуется изменить:** `GboardSamsungAppearanceSettings.java` + unit-тесты (покрытие всех 5 сценариев: сток, assets, Custom Theme ZIP, удалённый Custom Theme ZIP, ручная смена темы пользователем).
4. **Какие существующие компоненты будут затронуты:** DE `<pkg>_preferences`, CE `gboard_patches_settings`, `GboardBackupRestore` (исключить эфемерные поля снимка из кросс-устройственного экспорта).
5. **Какие функции потенциально могут пострадать:** сохранность исходной пользовательской темы и настройки контуров клавиш при многократном включении/выключении Samsung Appearance.
6. **Уровень риска:** **Средний** (ключи `additional_keyboard_theme` и `enable_key_border` подтверждены в `GboardCustomThemeInstaller.java`, но поведение `Gboard 18.5.4` при `remove("additional_keyboard_theme")` для возврата к стоковому Dynamic Color требует разовой проверки на устройстве в Этапе 2).
7. **Как проверить результат:** автоматические unit-тесты всех веток + ручной тест на Galaxy S22: (а) сток Dynamic Color → включить Samsung → выключить → вернулся Dynamic Color; (б) Custom Theme ZIP → включить Samsung → выключить → вернулась Custom Theme ZIP с исходным `enable_key_border`.
8. **Как откатить изменение:** ручной выбор любой темы в Gboard → «Тема» полностью перекрывает `additional_keyboard_theme`.

**Итоговый вердикт раздела 12:** **Можно реализовывать после проверки на устройстве**

---

## 13. Direct Boot и хранение ZIP

### 13.1. Подтверждённое расхождение CE vs DE в текущей кодовой базе

Прямое чтение `GboardCustomThemeInstaller.java` и `GboardPatchesSettings.java` выявило **архитектурно критический факт**:

| Данные | Файл в репозитории | Фактическое хранилище | Доступно в Direct Boot (до ввода PIN)? |
| --- | --- | --- | --- |
| ZIP-файлы тем (`user_theme_*.zip`) | `GboardCustomThemeInstaller.java` (стр. 38, 118, 126) | **`context.getFilesDir()` (CE — Credential-Encrypted)** | **НЕТ** (чтение/запись бросает `IllegalStateException` до разблокировки) |
| Ключи `additional_keyboard_theme` и `enable_key_border` | `GboardCustomThemeInstaller.java` (стр. 65–71) | **`context.createDeviceProtectedStorageContext().getSharedPreferences(...)` (DE — Device-Encrypted)** | **ДА** |
| Настройки патчей (`gboard_patches_settings`) | `GboardPatchesSettings.java` (стр. 21–28, 55–89) | **CE** с жёсткой защитой `isUserUnlocked(context)` | **НЕТ** (fail-closed → `null` / `fallback`) |

### 13.2. Почему ЗАПРЕЩЕНО вслепую переносить ZIP в DE-хранилище

На первый взгляд может возникнуть соблазн «исправить» Direct Boot, положив `samsung_oneui8_light.zip` и `samsung_oneui8_dark.zip` в `context.createDeviceProtectedStorageContext().getFilesDir()` (DE-хранилище).

**Это опасно и НЕДОПУСТИМО без доказательства из байткода `Gboard 18.5.4`**, по двум причинам:

1. В `GboardCustomThemeInstaller.java` префикс `"files:"` (`"files:" + committed.getName()`) работает на `18.5.4`, когда файл лежит в **CE `context.getFilesDir()`**. В репозитории **нет доказательств**, что внутренний загрузчик тем Gboard `18.5.4` ищет `"files:"` в DE `filesDir` (на Android `context.getFilesDir()` и `context.createDeviceProtectedStorageContext().getFilesDir()` — это **разные каталоги файловой системы**: `/data/user/0/<pkg>/files` против `/data/user_de/0/<pkg>/files`!).
2. Если положить ZIP только в DE-каталог, а Gboard `18.5.4` резолвит `"files:"` из CE `getFilesDir()`, тема **вообще не загрузится даже после разблокировки**. А если дублировать файл в оба каталога без понимания поведения Gboard — возникнет расхождение состояний и мусорные файлы.

### 13.3. Безопасная стратегия Direct Boot

1. **Хранить ZIP-файлы строго в том же каталоге, что и `GboardCustomThemeInstaller.java` (`context.getFilesDir()`, CE)** — это уже проверенный и работающий путь для `Gboard 18.5.4` в существующей фиче `Custom Theme`.
2. **Все runtime-хуки Samsung Appearance (`onStartInputView`, `onWindowShown`, `SoftKeyView.s`) MUST начинаться с `if (!GboardPatchesSettings.isUserUnlocked(context)) return;`** — точно так же, как уже сделано в `GboardFrostedGlassRuntime.java` и `GboardPatchesSettings.java`. Это гарантирует **0% риска `IllegalStateException`** в режиме Direct Boot со стороны нашего кода.
3. **Обязательный тест V8 на Galaxy S22 5G:** с активной пользовательской ZIP-темой (`"files:..."` в DE `SharedPreferences`) полностью перезагрузить смартфон и до первого ввода PIN проверить, корректно ли открывается системная клавиатура / GboardSamsung без крашей (если на экране PIN Samsung использует системную клавиатуру ввода PIN — также проверить сценарий защищённого поля до разблокировки). Если бы выяснилось, что сам Gboard `18.5.4` имеет проблему с `"files:"` до разблокировки, это затронуло бы и существующий `Custom Theme` и потребовало бы отдельного исследования загрузчика `"files:"` в APK `18.5.4`.

### 13.4. Спецификация потенциального изменения (политика хранения и Direct Boot)

1. **Точное место в исходном коде:** `GboardCustomThemeInstaller.java` (переиспользование без ломки пути CE `getFilesDir()`), `GboardPatchesSettings.isUserUnlocked(Context)` во всех новых runtime-классах.
2. **Точный механизм реализации:** установка ZIP в CE `context.getFilesDir()` только из Settings UI (когда пользователь гарантированно разблокирован); в IME runtime — строгий `isUserUnlocked(context)` guard перед любым обращением к CE-файлам или `gboard_patches_settings`.
3. **Какие файлы потребуется изменить:** новые классы в `extension/samsungappearance/` (используют существующие `GboardPatchesSettings.isUserUnlocked` и `GboardCustomThemeInstaller`).
4. **Какие существующие компоненты будут затронуты:** файловое хранилище CE `getFilesDir()`, DE `<pkg>_preferences`.
5. **Какие функции потенциально могут пострадать:** первый запуск клавиатуры после перезагрузки устройства (Direct Boot), стабильность IME при заблокированном CE-хранилище.
6. **Уровень риска:** **Средний** (код расширения гарантированно fail-closed, но реакция самого Gboard `18.5.4` на `"files:..."` в DE-преференсах при заблокированном CE-каталоге требует проверки V8 на Galaxy S22).
7. **Как проверить результат:** перезагрузка Galaxy S22 5G с активной Samsung ZIP-темой → проверка экрана ввода PIN до разблокировки → разблокировка → немедленное открытие клавиатуры (должна подняться Samsung-тема без ошибок).
8. **Как откатить изменение:** отключение режима Samsung Appearance удаляет ссылку `"files:..."` из DE `SharedPreferences` и возвращает исходное значение.

**Итоговый вердикт раздела 13:** **Можно реализовывать после проверки на устройстве**

---

## 14. Совместимость с Custom Theme, Frosted Glass и всеми функциями Gboard/GboardSamsung

Ниже проверена совместимость предполагаемой архитектуры Samsung Appearance со всеми 19 критическими функциями и режимами:

| # | Функция / режим | Механизм взаимодействия | Риск конфликта | Защитные меры |
| --- | --- | --- | --- | --- |
| 1 | **Custom Theme** (импорт ZIP и галерея) | Разделяют один ключ `additional_keyboard_theme` и каталог `getFilesDir()`. | Средний | Снимок/восстановление (Раздел 12), проверка ownership (ручной выбор Custom Theme не перезаписывается авто-переключателем), уникальные имена файлов Samsung ZIP. |
| 2 | **Frosted Glass** (размытие и прозрачность) | `GboardFrostedGlassRuntime` находит `.keyboard-base-area`, `.keyboard-header-area`, `.keyboard-body-area`, `.keytop`, `.space_bar`, `.background-icon.for-action-key` и меняет их `Drawable.setAlpha(...)`. | Средний | Samsung CSS **не должна** использовать несовместимые кастомные drawables/обёртки, блокирующие `setAlpha`. Проверить `Frosted Glass` поверх Samsung Light и Dark в Этапе 2 (включая `key opacity` и `surface opacity`). Шевроны в `ViewOverlay` не трогают `background` `.space_bar`, поэтому с `Frosted Glass` не конфликтуют. |
| 3 | **Swipe typing** (непрерывный ввод жестами) | Обрабатывается `ScrubMotionEventHandler` / движком Gboard. | Низкий | CSS-темы и пассивный `ViewOverlay` не касаются `MotionEvent`. Проверить контраст линии следа свайпа на `#EAEAEF` (Light) и `#08080A` (Dark)! |
| 4 | **Переключение языков** (клавиша глобуса и системные субтипы) | `GboardSpacebarGlobeKeyRuntime` (хук `SoftKeyView.s`, `afterOrder = 900`). | Низкий | Шевроны используют `afterOrder = 950` и фильтруют строго `KEYCODE_SPACE == 62`, никогда не скрывают глобус. |
| 5 | **Samsung Spacebar Gestures** (свайп по пробелу для смены языка) | Перехватывает `MotionEvent` на `KeyboardViewHolder` / `SoftKeyboardView`. | Низкий | `ViewOverlay` не перехватывает касания и не меняет размеры/хитбокс `SoftKeyView`. Обязателен регрессионный тест после Этапа 3. |
| 6 | **Удержание пробела и управление курсором** | `FreeCursorMotionEventHandler` / `TriggerFreeCursorMotionEventHandler`. | Низкий | `ViewOverlay` полностью пассивен; не вызывает `requestLayout` и не сбрасывает указатель. |
| 7 | **Clipboard** (буфер обмена + `Clipboard Content Limit`) | Отдельная клавиатура `ClipboardKeyboard` использует `.keyboard-base-area`, `.keyboard-header-area`, карточки буфера. | Средний | Проверить в Этапе 2, чтобы тёмный фон `#08080A` и светлый `#EAEAEF` сохраняли высокий контраст текста на карточках буфера и кнопках закрепления/удаления. |
| 8 | **Emoji** (панель эмодзи + `Emoji Size`) | Использует `.keyboard-base-area`, `.keyboard-header-area`, нижнюю навигацию категорий. | Средний | Проверить контраст активной вкладки, строки поиска эмодзи и нижней панели категорий в Light и Dark. |
| 9 | **GIF / Stickers** | Панели поиска и вкладок выражения (`Expression`). | Средний | Проверить контраст поля поиска GIF и вкладок в Light и Dark. |
| 10 | **Подсказки (Suggestions / Autocorrect / Inline Suggestions)** | Рендерятся в `.candidates-area` внутри `.keyboard-header-area`; тег `.for-candidate-key` специально оберегается даже в `Frosted Glass`. | Средний | В CSS задавать контрастный цвет текста кандидатов в Light и Dark, **не добавлять** непрозрачную заливку на неактивный `.for-candidate-key` и не цеплять круглые подложки к области кандидатов. |
| 11 | **Вторичные символы** (`.label.secondary`) | Верхний правый угол буквенных клавиш (включены у пользователя на скриншоте!). | Средний | Явно настроить контрастный цвет `.label.secondary` в Light (`#6B6E75`) и Dark (`#B0B3B8`), проверить читаемость в Этапе 2. |
| 12 | **Long press** (всплывающие альтернативные символы и quick actions) | Popup-окно над клавишей + `Long Press Quick Actions` (`Lphr;->aw`). | Средний | Проверить контраст фона и текста popup-подсказок в Light и Dark (частая ошибка сторонних CSS-тем — невидимый текст в popup!). |
| 13 | **Action Keys** (`Enter`, `Search`, `Send`, `Done`, `Next`, `Go`) | В `18.5.4` заливка `.background-icon.for-action-key` может быть `ImageView.getDrawable()`, отдельным от иконки (подтверждено в `GboardFrostedGlassRuntime:1071`). | Средний | Обязательная матрица проверки всех 6 типов Action Keys в Light и Dark на Этапе 2 (проверка V3). |
| 14 | **Нижняя навигационная панель (Nav bar)** | На Android 16 (`STOCK_STRIP_SDK = 36`) окрашивается самим Gboard в `InputView` через `Lrra;->f(Window, Lrqz;)`. | Средний | Проверить, подхватывает ли `Lrra;->f` цвет фона из Samsung ZIP-темы (`#EAEAEF` / `#08080A`) и корректно ли выставляется флаг `Lrqz;->e` (`light` для системных иконок навигации). |
| 15 | **Floating keyboard** | Использует `.keyboard-base-area` (без `.keyboard-outline`), произвольная ширина и ручка перемещения. | Средний | Проверить контраст ручки перетаскивания, отступы; авто-скрытие шевронов пробела при ширине `< 120dp` и отказ от тесных кругов в тулбаре. |
| 16 | **Split keyboard** (`split_eligibility`, `Lpqw;`) | Пробел делится на 2 узкие половинки; тулбар растягивается. | Средний | Авто-скрытие шевронов пробела, если ширина каждой половинки `< 120dp`; проверить симметрию клавиш и фона в разрыве. |
| 17 | **One-handed mode** | Клавиатура сжимается до ~80% ширины + боковая панель переключения стороны. | Средний | Проверить контраст боковых кнопок One-handed; авто-скрытие шевронов и круглых подложек при нехватке ширины. |
| 18 | **Разные раскладки и языки** (RU 11-кол., EN 10-кол., 123, Символы, Телефон/PIN, RTL) | Динамическая ширина клавиш и разные `SoftKeyDef`. | Средний | В RU 11 клавиш в ряду (уже, чем в EN 10) — проверить `padding_ratio_*` и размер шрифта, чтобы буквы и вторичные символы не обрезались; в `?123` и телефонной раскладке шевроны не должны появляться на не-пробельных клавишах. |
| 19 | **Key Shape Selection** (`more_pill_keys`) | Включённый флаг делает `?123` и `Enter` круглыми/pill (видно на скриншоте пользователя). | Средний | Сохранить патч `Key Shape Selection` нетронутым; в Этапе 2 (V2) выяснить реальный приоритет CSS над `more_pill_keys` и задокументировать совместимость. |

**Итоговый вердикт раздела 14:** **Можно реализовывать после проверки на устройстве**

---

## 15. Матрица рисков

Ни одной позиции не присвоен уровень `Низкий`, если отсутствует прямое доказательство в коде и/или на устройстве `18.5.4`.

| ID | Риск | Компонент / этап | Вероятность | Тяжесть | Итоговый уровень | Смягчение и триггер остановки |
| --- | --- | --- | --- | --- | --- | --- |
| **R1** | Искажение или поломка кнопки голосового ввода Rambler (двойной круг, обрезка анимации, баг захвата заголовка `Lflb`/`Lkaf`). | Тулбар (Этапы 4–5) | Высокая (при наивной реализации) | Критическая | **Высокий** | Явно не модифицировать Rambler; в Этапе 5 — только allowlist обычных AP; при невозможности 100% изоляции — **отмена Этапа 5**. |
| **R2** | Визуальное слипание или обрезка круглых подложек при `Access Point Count = 8` (9 слотов в строке) и в One-handed/Floating. | Тулбар (Этап 5) | Высокая | Высокая | **Высокий** | Динамический расчёт диаметра + авто-выключение при `< 34dp/слот`, либо полный отказ от Этапа 5. |
| **R3** | Нечитаемые Action Keys (`Enter`/`Search`/`Send`/`Done`/`Next`), popup long-press, подсказки или карточки буфера из-за особенностей CSS в `18.5.4`. | ZIP/CSS-темы (Этапы 1–2) | Средняя | Высокая | **Средний** | Обязательный чек-лист прогона всех 6 Action Keys + popup + Clipboard + Emoji + Suggestions на S22 в Этапе 2 до перехода к Этапу 3. |
| **R4** | `more_pill_keys` (Material 3) игнорирует CSS-форму `?123` / `Enter`. | ZIP/CSS-темы (Этап 2) | Средняя | Низкая/Средняя | **Средний** | Проверить в V2; не внедрять опасные байткод-хаки геометрии клавиш; документировать поведение с `Key Shape Selection`. |
| **R5** | Перенос ZIP в DE-хранилище ломает загрузку `"files:"` в `18.5.4`, либо CE-блокировка в Direct Boot вызывает сбой до ввода PIN. | Хранение ZIP / Direct Boot (Этапы 1, 2, 6) | Средняя (если переносить в DE) / Низкая (если оставить CE + guard) | Критическая | **Средний** | **Запрет переноса в DE без доказательства**; обязательный тест перезагрузки V8 на S22 до ввода PIN. |
| **R6** | Шевроны пробела смещены относительно keycap, наезжают на длинное название языка или остаются «призраками» на переиспользованной клавише. | Шевроны (Этап 3) | Средняя | Средняя | **Средний** | Привязка к внутреннему `.space_bar`, замер ширины `TextView`, порог `< 120dp`, `WeakHashMap` + мгновенная очистка при `keycode != SPACE`. |
| **R7** | Перезапись пользовательской `Custom Theme` авто-переключателем Light/Dark или потеря исходного `enable_key_border`. | Авто Light/Dark + Backup/Restore (Этап 6) | Низкая (при ownership check) | Средняя | **Средний** | Снимок с проверкой `contains()` + ownership check (если пользователь сменил тему вручную — авто-переключатель уступает). |
| **R8** | Задержка смены Light/Dark при открытой клавиатуре (Gboard не перезагружает тему мгновенно при изменении `SharedPreferences`). | Авто Light/Dark (Этап 6) | Средняя | Низкая | **Средний** | Проверить в V7; не применять `killProcess`; принять мягкое обновление на следующем `onStartInputView`, если мгновенного listener нет. |
| **R9** | Конфликт с `Frosted Glass` (прозрачность поверхностей/клавиш не применяется или даёт артефакты с Samsung CSS). | Совместимость (Этап 2) | Средняя | Средняя | **Средний** | Использовать только стандартные CSS-заливки без нестандартных обёрток; обязательный тест с `Frosted Glass` на 0%/50%/100% прозрачности. |
| **R10** | Попытка форсировать байткод-переписывание приватных классов рендера Gboard ради 100% копии One UI 8. | Любой этап | — | Критическая | **Недопустимый** | **Строго запрещено архитектурой аудита.** Визуальное сходство никогда не важнее стабильности и обновлений Gboard. |

**Итоговый вердикт раздела 15:** **Можно реализовывать после проверки на устройстве**

---

## 16. Рекомендуемая архитектура

Рекомендуется **трёхслойная модульная архитектура** с независимыми переключателями, жёсткой изоляцией отказов (fail-closed) и нулевыми изменениями существующих патчей жестов, Rambler, Custom Theme и Key Shape Selection:

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Настройки GboardSamsung (GboardPatchesSettingsActivity, RU + EN)           │
│ [Внешний вид Samsung One UI 8]                                             │
│  ├─ Режим темы: Выкл / Авто (система) / Светлая / Тёмная                   │
│  ├─ Шевроны на пробеле (‹ Язык ›): Вкл / Выкл (независимо!)                │
│  └─ (Условно, только после V6) Подложки панели инструментов: Выкл          │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ (CE: gboard_patches_settings + guard)
      ┌───────────────────────────────┼───────────────────────────────┐
      ▼                               ▼                               ▼
┌─────────────────────┐ ┌───────────────────────────┐ ┌───────────────────────────┐
│ СЛОЙ 1: ZIP/CSS     │ │ СЛОЙ 2: ViewOverlay       │ │ СЛОЙ 3: Auto Light/Dark   │
│ (Этапы 1, 2, 4)     │ │ (Этап 3)                  │ │ + Backup/Restore (Эт. 6)  │
│                     │ │                           │ │                           │
│ • samsung_light.zip │ │ • GboardSpacebarChevron   │ │ • GboardSamsungAppearance │
│ • samsung_dark.zip  │ │   Runtime                 │ │   Runtime + Settings      │
│ • Валидация через   │ │ • Хук SoftKeyView.s       │ │ • Хук Lphr;               │
│   Lrnw;->h(File)Z   │ │   (afterOrder = 950)      │ │   onStartInputView /      │
│ • Установка в CE    │ │ • Фильтр KEYCODE_SPACE    │ │   onWindowShown           │
│   getFilesDir()     │ │ • Пассивный Drawable в    │ │ • Snapshot/Restore        │
│ • Активация в DE    │ │   ViewOverlay (.space_bar │ │   additional_keyboard_    │
│   <pkg>_preferences │ │   bounds, >=120dp, text   │ │   theme & enable_key_     │
│ • Фон клавиш, фон   │ │   collision guard)        │ │   border + ownership      │
│   тулбара, цвета    │ │ • 0 касаний MotionEvent   │ │ • 0 I/O на горячем пути   │
└─────────────────────┘ └───────────────────────────┘ └───────────────────────────┘
```

### Ключевые архитектурные инварианты

1. **Пространство имён сохраняется:** все новые классы размещаются в `dev.jason.gboardpatches.extension.samsungappearance.*` и `dev.jason.gboardpatches.patches.gboard.features.samsungappearance.*` (совместимость с `extensions/gboard-patches.rve` и `Package Rename`).
2. **Независимость подфункций:** пользователь может включить тему Samsung **без** шевронов, либо включить шевроны `‹ Русский ›` поверх **любой** своей темы (включая сток Dynamic Color или Custom Theme), независимо от `Samsung Spacebar Gestures` и `Swipe Down to Dismiss`.
3. **Изоляция ошибок:** каждый runtime-вызов обёрнут в `try/catch (Throwable)` и `isUserUnlocked(context)`, никогда не бросает исключения в процесс Gboard.
4. **Нулевая модификация Rambler и обработки касаний:** пакет `rambler/`, `GboardSpacebarGestureRuntime`, `FreeCursorMotionEventHandler`, `ScrubMotionEventHandler` не изменяются.
5. **Опциональность Этапа 5:** круглые подложки Access Points не являются частью базового контракта; они могут быть добавлены только как отдельный отключаемый слой, если и только если эксперимент V6 на Galaxy S22 подтвердит 100% безопасность при 3–8 элементах, One-handed и рядом с Rambler.

**Итоговый вердикт раздела 16:** **Можно реализовывать после проверки на устройстве**

---

## 17. План безопасной реализации по этапам

Реализация строго разбита на **6 последовательных этапов** с обязательными воротами проверки (Gate) после каждого этапа. Переход к следующему этапу **запрещён**, пока не закрыты критерии приёмки текущего.

### Этап 1: Создание ZIP/CSS тем Light и Dark, оформление клавиш и фона

- **Цель:** подготовить два чистых, самодостаточных архива (`samsung_oneui8_light.zip` и `samsung_oneui8_dark.zip`) формата `format_version: 3` с базовой палитрой One UI 8 (фон, буквенные клавиши, функциональные клавиши, пробел, основной и вторичный текст, состояния `:pressed`, контрастные popup-подсказки и Action Keys).
- **Объём:** только ZIP/CSS-ассеты и их валидация контрактом `GboardThemeImportContract` (размеры, отсутствие path traversal, валидный JSON).
- **Результат этапа:** два готовых ZIP-файла, которые можно протестировать на реальном устройстве **через уже существующую функцию `Custom Theme` (импорт ZIP)** ещё до написания единой строчки нового runtime-кода!
- **Риск:** Средний.

### Этап 2: Проверка отображения на Gboard 18.5.4

- **Цель:** прогнать ZIP-темы Этапа 1 на реальном **Samsung Galaxy S22 5G (Android 16 / One UI 8, Gboard 18.5.4)** и закрыть гипотезы **V1, V2, V3, V8** (плюс `Frosted Glass`, `Clipboard`, `Emoji`, `Suggestions`, `Action Keys`, `Nav bar`, `Floating/Split/One-handed`, `Key Shape Selection`).
- **Ворота (Gate 2):**
  - Валидатор `Lrnw;->h(File)Z` принимает оба архива;
  - Все 6 Action Keys, secondary symbols, long-press popup, Clipboard, Emoji и Suggestions полностью читаемы в Light и Dark;
  - Зафиксировано фактическое поведение `more_pill_keys` и Direct Boot (перезагрузка до PIN);
  - Палитра и скругления откалиброваны по живому экрану S22.
- **Риск:** Средний (эксперимент полностью обратим — смена темы в Gboard за 2 секунды).

### Этап 3: Исследование и безопасная реализация шевронов пробела

- **Цель:** проверить внутреннюю геометрию `SoftKeyView` пробела на `18.5.4` (V4) и реализовать `GboardSpacebarChevronRuntime` (пассивный `Drawable` в `ViewOverlay` через `SoftKeyView.s`, `afterOrder = 950`, фильтр `KEYCODE_SPACE == 62`).
- **Ворота (Gate 3):**
  - Шевроны симметрично выровнены внутри `.space_bar` в RU и EN, цвет адаптируется к Light/Dark и `Frosted Glass`;
  - Автоматически скрываются в Split / узком Floating / при коллизии с длинным текстом;
  - Мгновенно исчезают при переключении на `?123`/цифры/эмодзи (нет артефактов переиспользования `SoftKeyView`);
  - 100% работоспособность тапа пробела, `Samsung Spacebar Gestures` и удержания пробела для курсора.
- **Риск:** Средний.

### Этап 4: Оформление фона и цветов верхнего тулбара

- **Цель:** добавить в проверенные ZIP/CSS-темы правила фона `.keyboard-header-area` и (только после подтверждения V5 на `18.5.4`) цвета иконок тулбара и текста подсказок — **не трогая кнопку Rambler**.
- **Ворота (Gate 4):**
  - Фон тулбара гармонично сочетается с телом клавиатуры в Light и Dark и корректно работает с `Frosted Glass`;
  - Иконки меню `⊞`, всех 3–8 Access Points и текста подсказок имеют высокий контраст;
  - Кнопка Rambler (`🎤✦`) и её анимации диктовки на 100% не затронуты.
- **Риск:** Средний.

### Этап 5: Эксперимент с круглыми подложками Access Points (условный)

- **Жёсткое предусловие:** запускается **ТОЛЬКО** если диагностика V6 на Galaxy S22 (`18.5.4`) докажет, что обычные Access Points можно детерминированно отличить от кнопки Rambler, кнопки меню `⊞` и области подсказок по типу/тегу (без индексов!), а также что адаптивный диаметр не создаёт слипания при `Access Point Count = 8` и в One-handed.
- **Объём (если допущен):** изолированный отключаемый слой с автоотключением при тесной ширине (`< 34dp/слот`) и нулевым влиянием на `AccessPointDragPopupView` и Rambler.
- **Штатный альтернативный выход:** если V6 выявляет нестабильность или тесноту при 8+1 элементах — **Этап 5 официально закрывается отказом** без ущерба для Этапов 1–4 и 6.
- **Риск:** Высокий.

### Этап 6: Автоматическое переключение Light/Dark с сохранением и восстановлением исходных настроек

- **Цель:** реализовать встроенную установку предпроверенных ZIP-тем из Settings UI, полную схему Backup/Restore (`additional_keyboard_theme` + `enable_key_border` с проверкой `contains()` и ownership), а также автоматическое переключение по `Configuration.UI_MODE_NIGHT_MASK` в `Lphr;` (с защитой `isUserUnlocked`) и полную RU/EN локализацию экрана настроек.
- **Ворота (Gate 6):**
  - Подтверждён сценарий V7 (переключение системной темы Light ↔ Dark);
  - Проверен полный цикл Backup/Restore для стока Dynamic Color и для пользовательской Custom Theme ZIP;
  - Ручной выбор другой темы в Gboard не перезаписывается;
  - Перезагрузка устройства (Direct Boot, V8) проходит без единого сбоя;
  - Все unit/Robolectric-тесты проекта проходят без регрессий.
- **Риск:** Средний.

**Итоговый вердикт раздела 17:** **Можно реализовывать после проверки на устройстве**

---

## 18. План тестирования

Тестирование проводится в два уровня: (A) локальные JVM/Robolectric unit-тесты в репозитории и (B) ручной приёмочный прогон на реальном **Samsung Galaxy S22 5G (`SM-S901U1`, Android 16 / One UI 8, `Gboard 18.5.4`, без root)**.

### 18.1. Автоматические JVM/Robolectric тесты (в коде после начала реализации)

1. **Валидация ZIP-ассетов:** `GboardThemeImportContract` принимает оба архива (`samsung_oneui8_light.zip`, `samsung_oneui8_dark.zip`), валидный `metadata.json`, нулевые ошибки path traversal и лимитов.
2. **Backup/Restore & Ownership:** 5 сценариев (сток без ключа, `assets:...`, `files:user_theme_...`, удалённый исходный файл, ручная смена темы пользователем поверх Samsung Appearance) + Direct Boot (`isUserUnlocked == false` → no-op).
3. **Шевроны пробела (`GboardSpacebarChevronRuntimeTest`):** привязка при `KEYCODE_SPACE`, немедленная очистка при переиспользовании `SoftKeyView` под другой keycode, скрытие при ширине `< 120dp`, скрытие при коллизии с широким текстом, выключение тоггла снимает все `Drawable` из `ViewOverlay`.
4. **Авто Light/Dark:** `UI_MODE_NIGHT_NO` → Light, `UI_MODE_NIGHT_YES` → Dark, повторный вызов при уже активной теме → 0 записей в `SharedPreferences`.
5. **Локализация RU/EN:** проверка наличия и непустоты всех новых ключей в `gboard_settings_text.xml` (RU и EN).

### 18.2. Приёмочная матрица на реальном Galaxy S22 5G (`Gboard 18.5.4`)

| Группа тестов | Конкретные сценарии | Ожидаемый результат |
| --- | --- | --- |
| **Базовый ввод и раскладки** | RU (11 колонок), EN (10 колонок), Цифры, `?123`, Символы, Телефон/PIN, вторичные символы, long-press popup. | Все буквы, символы и popup чётко читаемы в Light и Dark; нет обрезки текста. |
| **Пробел и жесты** | Быстрый тап пробела; свайп пробела влево/вправо (`Samsung Spacebar Gestures`); удержание пробела + движение курсора; свайп-набор текста; смена глобусом. | Шевроны `‹ Русский ›` / `‹ English (US) ›` ровно по краям; ни одного ложного срабатывания или залипания жестов. |
| **Action Keys** | Поля ввода: `Enter` (чат), `Search` (браузер/Google), `Send` (мессенджер), `Done`, `Next`, `Go`. | Все 6 кнопок контрастны и однозначно различимы в Light и Dark. |
| **Тулбар и Rambler** | `Access Point Count` = `3`, `6`, `8`; перетаскивание Access Points; Legacy и Modern `Access Points Menu`; 5 циклов запуска/остановки диктовки **Rambler**; `Manual Incognito`. | Тулбар ровный, нет наложений; Rambler и его анимации работают на 100% штатно. |
| **Панели Gboard** | `Clipboard` (обычные и закреплённые элементы), `Emoji`, `GIF`, `Suggestions` (подсказки + автоисправление). | Высокий контраст во всех панелях; при показе подсказок нет посторонних артефактов. |
| **Режимы и ориентация** | Портрет ↔ Ландшафт; `Floating`; `Split`; `One-handed` (левая/правая); нижняя полоса Nav bar (жесты и 3 кнопки). | Шевроны и элементы тулбара корректно скрываются/адаптируются при сжатии; Nav bar совпадает по цвету. |
| **Совместимость патчей** | `Frosted Glass` (выкл / 50% / макс. прозрачность); `Rounded Keyboard Panel`; `Key Shape Selection` (вкл/выкл); `Custom Theme` (вкл Samsung → выкл Samsung → возврат Custom Theme). | Ни один существующий патч не сломан; исходная тема и `enable_key_border` восстанавливаются точно. |
| **Авто Light/Dark и Direct Boot** | Переключение системной темы Android в шторке (при закрытой и открытой клавиатуре); полная перезагрузка Galaxy S22 → экран PIN до разблокировки → разблокировка → открытие клавиатуры. | Плавная смена Light ↔ Dark без крашей; ноль ошибок после перезагрузки в Direct Boot. |

**Итоговый вердикт раздела 18:** **Можно реализовывать безопасно**

---

## 19. План отката

Архитектура проектируется так, чтобы **каждый слой имел мгновенный пользовательский откат (без перепрошивки/переустановки APK)** и **независимый кодовый откат (по коммитам/этапам)**:

### 19.1. Пользовательский откат на устройстве (0 переустановок)

1. **Откат темы оформления (Этапы 1, 4, 6):**
   - Выключить `Внешний вид Samsung` в настройках GboardSamsung → автоматически восстанавливаются сохранённые `additional_keyboard_theme` и `enable_key_border`.
   - Альтернативный аварийный путь: открыть штатные настройки **Gboard → Тема** и выбрать любую стоковую или свою Custom Theme — `Ownership Check` немедленно уступает управление пользователю.
2. **Откат шевронов пробела (Этап 3):**
   - Выключить отдельный переключатель `Шевроны на пробеле` → `GboardSpacebarChevronRuntime` немедленно удаляет все `Drawable` из `ViewOverlay` всех отслеживаемых `SoftKeyView`.
3. **Откат круглых подложек тулбара (Этап 5, если будет реализован):**
   - Выключить отдельный переключатель `Подложки панели инструментов` → декоратор мгновенно снимается.
4. **Аварийный полный сброс (на случай гипотетического бага до разблокировки):**
   - Все runtime-хуки защищены `isUserUnlocked(context)` и `try/catch (Throwable)`, поэтому клавиатура не может «окирпичить» ввод PIN. В крайнем случае пользователь всегда может переключиться на системную «Клавиатуру Samsung» в системном переключателе IME на панели навигации и отключить/переустановить пропатченный GboardSamsung.

### 19.2. Кодовый откат в репозитории (поэтапная обратимость)

- Каждый из 6 этапов коммитится **отдельным изолированным PR/коммитом**.
- Отмена Этапа 5 (если провалит тесты) или Этапа 3 (шевроны) выполняется одним `git revert` без затрагивания ZIP-тем, `Custom Theme`, `Frosted Glass`, `Samsung Spacebar Gestures` или `Rambler`.
- Существующие патчи репозитория и `gboard-version-bindings.json` не переписываются деструктивно — новые вызовы добавляются только как независимые участники существующих композиторов (`SoftKeyView.s`, `Lphr;`).

**Итоговый вердикт раздела 19:** **Можно реализовывать безопасно**

---

## 20. Список нерешённых вопросов

Ниже честно зафиксированы все вопросы, на которые **невозможно дать окончательный ответ без эксперимента на реальном Galaxy S22 5G (`Gboard 18.5.4`) или инспекции проприетарного APK `18.5.4`**:

1. **Точный список рабочих CSS-селекторов в `Gboard 18.5.4`:** подтвердятся ли на практике сторонние селекторы `.keytop.dark`, `.keytop.for-action-key`, `.background-icon.for-action-key`, `.icon.for-action-key`, `.label.secondary`, `.icon.access-point-item`, `.background-icon.access-points-menu`, `elevation`, `shadow_color`? *(Закрывается в Этапах 2 и 4.)*
2. **Приоритет Material 3 `more_pill_keys` над CSS-формой клавиш `?123` и `Enter`:** возвращает ли `background_shape: rectangle` прямоугольную форму при включённом `Key Shape Selection` или требуется переключение `Key Shape Selection`? *(Закрывается в Этапе 2, тест V2.)*
3. **Внутренняя структура `SoftKeyView` пробела в `18.5.4`:** имеет ли дочерний view keycap точный тег `.space_bar`, какие у него `padding`/отступы относительно `SoftKeyView`, и каков ID/способ поиска `TextView` метки языка для точного расчёта зазора до шевронов? *(Закрывается в Этапе 3, тест V4.)*
4. **Точная View-иерархия верхнего тулбара `18.5.4` и изоляция Rambler:** какими тегами/классами/ID в `18.5.4` представлены кнопка меню `⊞`, обычные Access Points (3–8 шт.), кнопка Rambler (`🎤✦`) и область подсказок, и возможно ли 100% безопасно отделить обычные Access Points от Rambler без опоры на индекс? *(Закрывается в пред-гейте V6 перед Этапом 5; при отрицательном ответе Этап 5 отменяется.)*
5. **Мгновенная перезагрузка темы в `18.5.4` при записи `additional_keyboard_theme`:** обновляется ли уже открытый `InputView` сразу при изменении DE `SharedPreferences` в `onStartInputView`/`onConfigurationChanged`, или смена вступает в силу при следующем показе клавиатуры? *(Закрывается в Этапе 6, тест V7.)*
6. **Поведение Gboard `18.5.4` в Direct Boot при `"files:..."` в DE `SharedPreferences` и заблокированном CE `getFilesDir()`:** откатывается ли Gboard бесшумно к стоковой теме до ввода PIN, и читает ли его загрузчик `"files:"` исключительно CE `filesDir`? *(Закрывается в Этапе 2 и Этапе 6, тест V8.)*
7. **Оптимальный визуальный вариант Action Key в `18.5.4`:** будет ли стабильнее и ближе к эталону Samsung One UI 8 вариант «функциональная клавиша + синяя иконка (`#2A6BF2` / `#4D8BFF`)» или вариант «синяя заливка + белая иконка» для всех 6 типов (`Enter`, `Search`, `Send`, `Done`, `Next`, `Go`)? *(Закрывается в Этапе 2, тест V3.)*
8. **Влияние `enable_key_border` и `lock_key_border` в `metadata.json` на отображение границ/теней и `Frosted Glass`:** как ведёт себя Samsung ZIP-тема при включённом `Frosted Glass` с разной прозрачностью клавиш и фона? *(Закрывается в Этапе 2.)*

**Итоговый вердикт раздела 20:** **Требуется дополнительное исследование**
