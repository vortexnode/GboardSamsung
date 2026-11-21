# Технический отчёт: реализация настраиваемого Package Name в GboardSamsung

## 1. Краткое резюме выполненной работы

В существующий публичный патч **`Package Rename`** (`gboardPackageRenamePatch`) репозитория `vortexnode/GboardSamsung` добавлена полноценная, типобезопасная и валидируемая настройка Android package ID (`package_name`) по архитектуре upstream PR #63 проекта `jasonwu1994/Gboard-patches`, адаптированной под требования GboardSamsung:

- Сохранена полная независимость и неизменность опции **`app_display_name`** (все 5 пресетов `GboardSamsung`, `Gboard`, `Gboard Dev`, `Gboard Patches`, `Gboard Mod`, ввод собственного имени, существующая валидация и механизм применения через `@string/gboard_patches_app_display_name`).
- Добавлена опция **`package_name`** (`kotlin.String`, `required = true`) с поддержкой **трёх режимов**:
  1. **По умолчанию (side-by-side):** `dev.jason.com.google.android.inputmethod.latin`.
  2. **Официальный пакет Google (специальный режим GboardSamsung):** `com.google.android.inputmethod.latin` — принимается валидатором, работает как безопасный семантический no-op без конфликтов маппинга, без двойных префиксов и с сохранением независимой работы `app_display_name`.
  3. **Произвольный Android package ID:** любой корректный пакет (например, `com.example.gboardsamsung` или `io.vortexnode.gboard_2`).
- Все 16 package-зависимых атрибутов `AndroidManifest.xml`, `GboardPatchesSettingsProvider`, оба файла настроек (`res/xml/settings.xml`, `res/xml/settings_legacy.xml`) и проверка пакета в `GboardPatchesSettingsActivity` синхронизированы для любого выбранного `package_name`.
- Добавлены и расширены автоматические тесты (transform, валидация, 3 режима, идемпотентность, контракт реестра, побайтовое соответствие `patches-list.json`, runtime-проверка `GboardPatchesSettingsActivity`).

---

## 2. Исходное состояние репозитория и базовая точка

- **Репозиторий:** `https://github.com/vortexnode/GboardSamsung`
- **Целевая ветка-база:** `main`
- **Базовый коммит `main`:** `600222ff7e1ef72800c048215c364a7b48d0e0ae` (`chore: Release v4.4.0 [skip ci]`, `gradle.properties`: `version = 4.4.0`)
- **Целевая версия Gboard:** `18.5.4.993608717-release-arm64-v8a`
- **Открытые PR на момент синхронизации (не затрагивались):**
  - PR #7 (`fix/gemini-ci-and-audit`)
  - PR #8 (`fix/gemini-main-russian-tests`)
- **Релиз `v4.3.0` и его ассеты:** не изменялись.
- **Ограничение текущей облачной среды AI Studio:** в контейнере отсутствует каталог `.git`, а также отсутствуют переменные окружения с токенами (`GITHUB_TOKEN`, `MORPHE_PACKAGES_TOKEN`). В связи с этим создание новой ветки, коммит, push, открытие нового PR и запуск Gradle-задач с плагином `app.morphe.patches:2.1.0` из GitHub Packages должны быть выполнены из Git-окружения с доступом к GitHub/Morphe Packages. Все изменения исходного кода, тестов, `patches-list.json` и отчёта полностью подготовлены и верифицированы локально на JVM.

---

## 3. Анализ реализации Jason Wu (PR #63) и адаптация под GboardSamsung

Изучен upstream PR `https://github.com/jasonwu1994/Gboard-patches/pull/63` (`feat: make the Package Rename package name configurable`, head `25333907a9`).

### Что взято из архитектуры Jason Wu PR #63
- Константа `GBOARD_DEFAULT_PATCHED_PACKAGE_NAME = "dev.jason.com.google.android.inputmethod.latin"` (с сохранением алиаса `GBOARD_PATCHED_PACKAGE_NAME` для обратной совместимости внутренних ссылок).
- Потокобезопасный контейнер цели `GboardPackageRenameTarget` (`@Volatile var packageName: String = GBOARD_DEFAULT_PATCHED_PACKAGE_NAME`), заполняемый в `execute { ... }` публичного патча `gboardPackageRenamePatch` и считываемый в `finalize { ... }` зависимого `gboardPackageRenameResourcePatch`.
- Динамический генератор явного списка из 16 маппингов `gboardPackageRenameMappings(targetPackage)` вместо жёстко зашитого списка (никакой глобальной строковой замены по APK).
- Расширение `GboardPatchesSettingsActivity.isSupportedTargetPackage(String packageName, String ownPackageName)` для безоговорочного признания собственного runtime `getPackageName()` при открытии Developer Options / Flag Editor.

### Ключевые отличия и доработки для GboardSamsung
1. **Поддержка официального режима `com.google.android.inputmethod.latin`:**
   - В upstream PR #63 значение `com.google.android.inputmethod.latin` отклонялось валидатором (`value != GBOARD_PACKAGE_NAME`).
   - В GboardSamsung значение `com.google.android.inputmethod.latin` **явно разрешено**, вынесено вторым готовым пресетом в `values` опции `package_name` и обрабатывается выделенной веткой `keepOriginalPackage = (targetPackage == GBOARD_PACKAGE_NAME)` с возвратом `GboardPackageRenameResult.UNCHANGED` (чистый семантический no-op без конфликтов `originalMatches` vs `renamedMatches` и без лишней модификации XML).
2. **LAN FTP отсутствует в GboardSamsung:**
   - В upstream PR #63 присутствует проверка `LanFtpStatusProvider`, однако в GboardSamsung функция LAN FTP была ранее удалена в рамках очистки 12 лишних функций. Проверка подтвердила отсутствие каких-либо упоминаний `lanftp`/`lan_ftp`/`LanFtp` в `patches/src` и `extensions/extension/src`; удалённая функциональность не возвращалась.
3. **Защита от двойных префиксов для произвольных имён пакетов:**
   - `buildDoublePrefixMarkers(targetPackage)` проверяет не только исторический `dev.jason.dev.jason.com.google.android.inputmethod.latin`, но и повторённый пользовательский префикс (`<prefix><prefix>com.google.android.inputmethod.latin` при суффиксе `.com.google.android.inputmethod.latin`), а также `<targetPackage>.<targetPackage>`.
4. **Двуязычные метаданные (EN / RU) без иероглифов (0 CJK):**
   - Описания патча `Package Rename`, опции `package_name` и предупреждения `gboard_patches_developer_options_package_warning_message` оформлены на английском и русском языках; в `GboardPackageRenameResourcePatch.kt` убрано устаревшее внутреннее описание на китайском языке.

---

## 4. Полный список package-зависимых сущностей и идентификаторов

| № | Файл / компонент | Элемент / символ | Исходное значение / шаблон | Поведение при `targetPackage` |
|---|---|---|---|---|
| 1 | `AndroidManifest.xml` | `manifest@package` | `com.google.android.inputmethod.latin` | `<targetPackage>` (в режиме 2 — без изменений) |
| 2 | `AndroidManifest.xml` | `permission@android:name` | `<orig>.pixelbundle.RECEIVER` | `<targetPackage>.pixelbundle.RECEIVER` |
| 3 | `AndroidManifest.xml` | `permission@android:name` | `<orig>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `<targetPackage>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |
| 4 | `AndroidManifest.xml` | `meta-data@android:name` | `com.google.android.gms.phenotype.registration.binarypb:<orig>` | `com.google.android.gms.phenotype.registration.binarypb:<targetPackage>` |
| 5 | `AndroidManifest.xml` | `meta-data@android:name` | `com.google.android.gms.phenotype.registration.xml:<orig>` | `com.google.android.gms.phenotype.registration.xml:<targetPackage>` |
| 6 | `AndroidManifest.xml` | `provider@android:authorities` (`SettingsSearchIndexablesProvider`) | `<orig>` | `<targetPackage>` |
| 7 | `AndroidManifest.xml` | `data@android:host` (`LinkReceivingLauncherActivity`) | `deeplink.<orig>` | `deeplink.<targetPackage>` |
| 8 | `AndroidManifest.xml` | `provider@android:authorities` (`ClipboardContentProvider`) | `<orig>.clipboard_content` | `<targetPackage>.clipboard_content` |
| 9 | `AndroidManifest.xml` | `provider@android:authorities` (`InputActionFileProvider`) | `<orig>.inputactionprovider` | `<targetPackage>.inputactionprovider` |
| 10 | `AndroidManifest.xml` | `provider@android:authorities` (`CrashResistantSwissArmyKnifeFileProvider`) | `<orig>.swissarmyknifefileprovider` | `<targetPackage>.swissarmyknifefileprovider` |
| 11 | `AndroidManifest.xml` | `provider@android:authorities` (`MainProcessInitializationProvider`) | `<orig>.androidx-startup` | `<targetPackage>.androidx-startup` |
| 12 | `AndroidManifest.xml` | `provider@android:authorities` (`CrashResistantFileProvider`) | `<orig>.fileprovider` | `<targetPackage>.fileprovider` |
| 13 | `AndroidManifest.xml` | `receiver@android:permission` (`PixelBundleBroadcastReceiver`) | `<orig>.pixelbundle.RECEIVER` | `<targetPackage>.pixelbundle.RECEIVER` |
| 14 | `AndroidManifest.xml` | `provider@android:authorities` (`ColdStartupTraceContentProvider`) | `<orig>.tracing` | `<targetPackage>.tracing` |
| 15 | `AndroidManifest.xml` | `provider@android:authorities` (`WebDebugBridgeContentProvider`) | `<orig>.wdb` | `<targetPackage>.wdb` |
| 16 | `AndroidManifest.xml` | `provider@android:authorities` (`MlKitInitProvider`) | `<orig>.mlkitinitprovider` | `<targetPackage>.mlkitinitprovider` |
| 17 | `AndroidManifest.xml` | `provider@android:authorities` (`GboardPatchesSettingsProvider`) | `<orig>.gboard_patches` | `<targetPackage>.gboard_patches` (обе последовательности финализации) |
| 18 | `res/xml/settings.xml` | `intent@android:targetPackage` (`GboardPatchesSettingsActivity`) | `<orig>` | `<targetPackage>` |
| 19 | `res/xml/settings_legacy.xml` | `intent@android:targetPackage` (`GboardPatchesSettingsActivity`) | `<orig>` | `<targetPackage>` |
| 20 | `AndroidManifest.xml` (split markers) | `manifest@android:requiredSplitTypes`, `manifest@android:splitTypes`, `meta-data[com.android.vending.splits.required]` | присутствуют в split APK | Удаляются при переименовании (режимы 1 и 3); сохраняются нетронутыми в режиме 2 |
| 21 | `GboardPatchesSettingsProvider.java` & `GboardSymbolFooterOrderRuntime.java` | Authority lookup | `context.getPackageName() + ".gboard_patches"` | Динамически привязан к runtime `getPackageName()` (уже универсально) |
| 22 | `GboardAdvancedVoiceSettingsFeature.java` | `targetPackageNames(Context)` | `context.getPackageName()` первым элементом + fallback-пакеты | Динамически поддерживает любой `getPackageName()` (проверено) |
| 23 | `GboardPatchesSettingsActivity.java` | `isSupportedTargetPackage(packageName, ownPackageName)` | Проверка собственного пакета + официальный/default/legacy | Обновлено: любой настроенный собственный пакет принимается без предупреждения |
| 24 | `gboard_settings_text.xml` | `gboard_patches_developer_options_package_warning_message` | EN/RU сообщение с `%1$s` | Обновлено: не ограничивает текст только `dev.jason.com.google.android.inputmethod.latin` |
| 25 | Классы компонентов и расширений | `dev.jason.gboardpatches.*`, `com.google.android.apps.inputmethod.*`, `com.android.inputmethod.latin.*`, `<original-package>` | Неизменны | Намеренно **не** переименовываются |

---

## 5. Описание трёх режимов работы `package_name`

1. **Режим 1 — по умолчанию (`dev.jason.com.google.android.inputmethod.latin`):**
   - Переименовывает пакет и все 16 атрибутов манифеста + `GboardPatchesSettingsProvider` + `targetPackage` в `settings.xml` и `settings_legacy.xml` в `dev.jason.com.google.android.inputmethod.latin`.
   - Удаляет маркеры обязательных split-зависимостей (`requiredSplitTypes`, `splitTypes`, `com.android.vending.splits.required`) для корректной автономной установки.
   - Возвращает `GboardPackageRenameResult.RENAMED` при первом применении и `ALREADY_RENAMED` при повторном.
   - Обеспечивает установку рядом с официальным Gboard без конфликтов.

2. **Режим 2 — официальный пакет (`com.google.android.inputmethod.latin`):**
   - Валидирует корневой пакет `manifest@package == "com.google.android.inputmethod.latin"`, все 16 маппингов (ровно по 1 совпадению без ложной проверки `oppositeMatches`), отсутствие двойных префиксов, отсутствие неожиданных package-производных атрибутов и консистентность настроек (`com.google.android.inputmethod.latin.gboard_patches` и `targetPackage="com.google.android.inputmethod.latin"`).
   - Не мутирует DOM манифеста и `settings*.xml` (чистый no-op) и возвращает `GboardPackageRenameResult.UNCHANGED`.
   - Повторный вызов полностью идемпотентен (`UNCHANGED`).
   - Опция `app_display_name` продолжает работать независимо, позволяя переименовать отображаемое имя приложения при сохранении официального ID пакета.

3. **Режим 3 — пользовательский Android package ID (например, `com.example.gboardsamsung`):**
   - Проходит строгую валидацию до любой мутации DOM.
   - Переименовывает все 16 атрибутов манифеста, удаляет обязательные split-маркеры и синхронизирует `GboardPatchesSettingsProvider` (`<custom>.gboard_patches`) и `targetPackage="<custom>"` в обоих файлах настроек, независимо от порядка финализации патчей (`Package Rename -> Settings` или `Settings -> Package Rename`).
   - Возвращает `RENAMED`, при повторном применении с тем же ID — `ALREADY_RENAMED` (точный no-op), при несовпадающем корневом пакете — `IllegalStateException` до мутации.

---

## 6. Как сохранена совместимость с `app_display_name`

- Ключ опции (`app_display_name`), значение по умолчанию (`GboardSamsung`), заголовок (`App name / Название приложения`), описание (`""`), флаг `required = true`, тип `kotlin.String` и все 5 пресетов (`GboardSamsung`, `Gboard`, `Gboard Dev`, `Gboard Patches`, `Gboard Mod`) остались **идентичными**.
- Валидация `isValidGboardAppDisplayName` и применение `applyGboardApplicationDisplayName` через выделенный ресурс `res/values/strings.xml` (`gboard_patches_app_display_name`) и `PublicXmlManager` не изменялись.
- `package_name` и `app_display_name` работают как две полностью независимые опции; автоматические тесты проверяют все комбинации: default/official/custom `package_name` × пресет/кастомный `app_display_name`.

---

## 7. Изменения в метаданных патча и `patches-list.json`

В `GboardPatchRegistry.kt` и `patches-list.json` для патча **`Package Rename`** (`use: true`):
- Обновлено двуязычное описание (`description`) на EN + RU.
- Добавлена опция `package_name`:
  - `key`: `"package_name"`
  - `title`: `"Package name / Имя пакета"`
  - `description`: двуязычное пояснение EN + RU для всех трёх режимов
  - `required`: `true`
  - `type`: `"kotlin.String"` (в Morphe Manager 2.x допускает как выбор из `values`, так и свободный ввод пользовательской строки — по тому же механизму, что и `app_display_name`)
  - `default`: `"dev.jason.com.google.android.inputmethod.latin"`
  - `values`:
    - `"dev.jason.com.google.android.inputmethod.latin": "dev.jason.com.google.android.inputmethod.latin"`
    - `"com.google.android.inputmethod.latin": "com.google.android.inputmethod.latin"`
- Опция `app_display_name` сохранена полностью без изменений.
- Побайтовое совпадение вывода `GboardPublishedPatchCatalog.publishedInventory("4.4.0")` с файлом `patches-list.json` подтверждено тестом `InventoryParityTest`.
- Файл `patches-bundle.json` не изменялся (указывает на опубликованный релизный бандл `v4.4.0` и обновляется только релизным workflow).

---

## 8. Правила валидации `package_name`

Реализованы в `isValidGboardPackageName(value: String?)` и `isValidGboardTargetPackageName(value: String?)`:

- **Допускаются:**
  - `dev.jason.com.google.android.inputmethod.latin`
  - `com.google.android.inputmethod.latin`
  - Любые корректные Android package ID: минимум 2 сегмента через точку, каждый сегмент начинается с латинской буквы `[A-Za-z]` и содержит только `[A-Za-z0-9_]`, длина `<= 255` символов (регулярное выражение `^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$`).
- **Отклоняются (до любых изменений APK / DOM):**
  - `null`, пустая строка `""`, пробелы в начале/конце/внутри (`" "`, `" com.a.b"`, `"com.a.b "`, `"com.a.my app"`)
  - Односегментные имена (`"gboard"`, `"com"`)
  - Ведущие и замыкающие точки (`".com.a.b"`, `"com.a.b."`)
  - Пустые сегменты / двойные точки (`"com..a.b"`)
  - Сегменты, начинающиеся с цифры (`"1com.a.b"`, `"com.1a.b"`)
  - Дефисы и недопустимые символы (`"com.a.b-c"`, `"com.a.b$"`, `"com.a/b"`, кириллица, управляющие символы `\n`)
  - Строки длиннее 255 символов.
- Проверка выполняется на трёх уровнях: валидатор `stringOption`, блок `execute { ... }` в `GboardPatchRegistry.kt` и `require(...)` в начале `gboardPackageRenameMappings` и `applyGboardPackageRename`.

---

## 9. Изменения в runtime и настройках (Settings / Developer Options)

- **`extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/GboardPatchesSettingsActivity.java`:**
  - Метод `isSupportedTargetPackage` изменён с `private static boolean isSupportedTargetPackage(String packageName)` на package-private `static boolean isSupportedTargetPackage(String packageName, String ownPackageName)`.
  - Если `packageName` не `null` и не пустой и равен `ownPackageName` (`getPackageName()`), возвращается `true`. Также по-прежнему поддерживаются `com.google.android.inputmethod.latin`, `com.google.android.inputmethod.latin.jason.dev` и `dev.jason.com.google.android.inputmethod.latin`.
  - В `openTargetSettingsHeader` вызов обновлён на `isSupportedTargetPackage(currentPackage, getPackageName())`.
- **`extensions/extension/src/main/settings-text/gboard_settings_text.xml`:**
  - Обновлены EN и RU переводы `gboard_patches_developer_options_package_warning_message` с сохранением плейсхолдера `%1$s` и упоминанием официального пакета и имени пакета из патча `Package Rename`.
- **`GboardDeveloperOptionsFeatureMarkerPatch.kt`:** не изменялся — на Android 11+ приложение всегда имеет неявную видимость собственного пакета (`getPackageName()`), поэтому `getPackageInfo(currentPackage, 0)` работает для любого пользовательского `package_name` без дополнительных `<queries>`.

---

## 10. Список изменённых и добавленных файлов

### Изменённые файлы (9)
1. `patches/src/main/kotlin/dev/jason/gboardpatches/patches/shared/Constants.kt`
2. `patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/packagerename/GboardPackageRenameResourcePatch.kt`
3. `patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/GboardPatchRegistry.kt`
4. `extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/GboardPatchesSettingsActivity.java`
5. `extensions/extension/src/main/settings-text/gboard_settings_text.xml`
6. `patches-list.json`
7. `patches/src/test/kotlin/dev/jason/gboardpatches/patches/gboard/features/packagerename/GboardPackageRenameTransformTest.kt`
8. `patches/src/test/kotlin/dev/jason/gboardpatches/patches/gboard/registry/GboardPackageRenamePatchContractTest.kt`
9. `patches/src/test/kotlin/dev/jason/gboardpatches/patches/gboard/registry/GboardDeveloperOptionsPatchContractSourceTest.kt`

### Новые файлы (2)
1. `extensions/extension/src/test/java/dev/jason/gboardpatches/extension/settings/GboardPatchesSettingsActivityPackageTest.java`
2. `docs/reports/package-rename-implementation-report.md`

### Удалённые файлы
- Нет (0 удалённых файлов репозитория).

---

## 11. Добавленные и обновлённые автоматические тесты

- **`GboardPackageRenameTransformTest.kt`** (48 JUnit 4 тестов):
  - Все исходные тесты `app_display_name` и 16 маппингов сохранены.
  - Валидация `isValidGboardPackageName` / `isValidGboardTargetPackageName` (позитивные и негативные случаи, граница 255/256 символов).
  - Отклонение невалидного `targetPackage` до мутации DOM.
  - Режим 2 (`com.google.android.inputmethod.latin`): чистый no-op (`UNCHANGED`), идемпотентность, оба порядка финализации с `applyGboardPatchesSettings`, отказ при уже переименованном корневом пакете, отказ при неожиданном package-атрибуте.
  - Режим 3 (`com.example.gboardsamsung` и `com.vortex.com.google.android.inputmethod.latin`): точное переименование всех 16 атрибутов, `clipboard_content`, permissions, receiver permission, metadata, `GboardPatchesSettingsProvider`, обоих `settings*.xml`, оба порядка финализации, идемпотентность (`ALREADY_RENAMED`), защита от повторного префикса и самоповтора, отказ при несовпадающем уже переименованном корне, неизменность имён классов расширений `dev.jason.gboardpatches.*`.
  - Проверка контейнера `GboardPackageRenameTarget`.
  - Независимая комбинация `app_display_name` × `package_name` во всех трёх режимах.
- **`GboardPackageRenamePatchContractTest.kt`** (3 теста):
  - Проверка обеих опций (`package_name` и `app_display_name`), их заголовков, описаний, типов, флага `required`, значений по умолчанию, пресетов `values`, валидатора и сгенерированного каталога.
- **`GboardDeveloperOptionsPatchContractSourceTest.kt`** (3 теста):
  - Обновлена проверка вызова `isSupportedTargetPackage(currentPackage, getPackageName())`.
- **`GboardPatchesSettingsActivityPackageTest.java`** (3 теста):
  - Проверка поддержки собственного кастомного пакета, официального/default/legacy пакетов и отклонения посторонних/`null`/пустых пакетов.

---

## 12. Результаты сборок, тестов и проверок

### Успешно выполнено локально в текущей среде
| Проверка | Инструментарий | Результат |
|---|---|---|
| Компиляция и прогон `GboardPackageRenameTransformTest` + `GboardDeveloperOptionsPatchContractSourceTest` | Kotlin `K2JVMCompiler 2.2.21` (`-Xcontext-parameters -jvm-target 17`) + `JUnitCore 4.13.2` | **PASS — OK (51 tests, 1.344s)** |
| Компиляция `GboardPatchRegistry.kt` и прогон `GboardPackageRenamePatchContractTest`, `GboardPublishedPatchCatalogContractTest`, `GboardDeveloperOptionsGeneratedInventoryTest`, `InventoryParityTest` | Kotlin `K2JVMCompiler 2.2.21` + `JUnitCore 4.13.2` + `gson-2.13.1` | **PASS — OK (8 tests, 0.262s)** (включая побайтовое совпадение `patches-list.json`) |
| Компиляция всех 238 Java-файлов модуля `extensions/extension` и прогон `GboardPatchesSettingsActivityPackageTest`, `GboardSettingsAccessArchitectureTest`, `GboardPatchesSettingsActivityToggleSourceTest` | `javac --release 17` + `android-36/android.jar` + `JUnitCore 4.13.2` | **PASS — OK (6 tests, 0.342s)** |
| Валидация `patches-list.json` (JSON, 34 патча, 2 опции на `Package Rename`, 0 CJK) | Python `json` + regex | **PASS** |
| Валидация `gboard_settings_text.xml` (XML, 627 strings + 3 plurals, `%1$s` parity, 0 CJK) | Python `xml.etree.ElementTree` | **PASS** |
| Проверка гигиены диффа (trailing whitespace, conflict markers, точный список файлов относительно `main` `600222f`) | Python diff-аудит | **PASS (0 замечаний)** |

### Не выполнено в текущей среде (требует GitHub / `MORPHE_PACKAGES_TOKEN`)
- `gradle :patches:test`, `gradle :extensions:extension:testDebugUnitTest` (включая Robolectric-тесты), `gradle :patches:buildAndroid` (сборка `.mpp` бандла):
  - **Причина:** при запуске `gradle` в контейнере резолвер плагинов завершается ошибкой `Plugin [id: 'app.morphe.patches', version: '2.1.0'] was not found`, так как репозиторий GitHub Packages `https://maven.pkg.github.com/MorpheApp/registry` требует секрет `MORPHE_PACKAGES_TOKEN`, отсутствующий в данном контейнере.
- Git-операции (`git checkout -b`, `git commit`, `git push`), создание PR и прогон GitHub Actions CI:
  - **Причина:** в рабочем пространстве контейнера отсутствует `.git` и учётные данные GitHub.

---

## 13. Проверка GitHub Actions и статуса PR

- В данной среде GitHub Actions не запускался (из-за отсутствия `.git` и `GITHUB_TOKEN` в контейнере).
- Существующие PR #7 и PR #8, ветка `main` на GitHub и релиз `v4.3.0` не изменялись.
- После пуша новой ветки (например, `feat/configurable-package-name`) из локального/CI Git-окружения с `MORPHE_PACKAGES_TOKEN` необходимо убедиться в прохождении стандартных workflow-проверок (`:extensions:extension:testDebugUnitTest`, `:patches:test`, сборка `.mpp` и проверка `patches-list.json`).

---

## 14. Проверенные ограничения, риски и edge cases

1. **Идентичность пакета Android и обновления поверх установленной версии:**
   - В Android `package_name` — это уникальный идентификатор приложения. Сборка с новым `package_name` (например, `com.example.gboardsamsung`) **не может** обновиться поверх `dev.jason.com.google.android.inputmethod.latin` и устанавливается как отдельное приложение со своим изолированным хранилищем настроек, словарей и буфера обмена.
2. **Официальный режим (`com.google.android.inputmethod.latin`) и подпись APK:**
   - Так как пропатченный APK подписывается пользовательским ключом, установить его поверх официального Gboard из Google Play / системного раздела без удаления/замены (root или предварительного удаления обновлений/пакета) невозможно из-за несовпадения подписи Android (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
   - В режиме 2 маркеры split APK не удаляются патчем `Package Rename`; при сборке из split-бандла (`.apkm`/`.xapk`) необходимо использовать предварительно объединённый/standalone APK (как того требует Morphe).
3. **Уведомление при попытке повторного переименования в другой пакет:**
   - Если входной APK уже переименован в пакет `A`, а пользователь пытается применить `Package Rename` с другим целевым пакетом `B`, патч намеренно завершится ошибкой до мутации манифеста.
4. **Неизменность `dev.jason.gboardpatches`:**
   - Пространство имён классов расширений и `extensions/gboard-patches.rve` не меняются — это гарантирует совместимость с bytecode-хуками и RVE-инъекцией.
5. **Оффлайн-верификация с заглушками Morphe:**
   - Локальный запуск Kotlin-тестов выполнен с минимальными stub-обёртками Morphe Patcher 2.x; финальный прогон с настоящим артефактом `app.morphe.patches:2.1.0` и реальным `XmlEncodeUtil` / `PublicXmlManager` должен пройти в GitHub Actions CI.

---

## 15. План ручного тестирования на реальном устройстве

Рекомендуемая матрица проверки на целевом устройстве (**Samsung Galaxy S22 5G, Android 16 / One UI 8, Gboard 18.5.4.993608717-release-arm64-v8a, Morphe Manager**):

1. **Сценарий A (Режим 1 — Default):**
   - `package_name = dev.jason.com.google.android.inputmethod.latin`, `app_display_name = GboardSamsung`.
   - Проверить: установка рядом с официальным Gboard, отображение имени `GboardSamsung`, вход в Настройки -> `Patches`, работа Developer Options / Flag Editor без предупреждения «Unsupported Gboard package», работа Clipboard, Backup & Restore, Spacebar Gestures, Swipe Dismiss, Themes.
2. **Сценарий B (Режим 3 — Custom package + Custom display name):**
   - `package_name = com.example.gboardsamsung`, `app_display_name = My Custom Board`.
   - Проверить: отображение в Morphe Manager (пресеты + ввод своего значения), успешная сборка APK и установка рядом с официальным Gboard и дефолтной сборкой, отображаемое имя `My Custom Board`, открытие `Patches` из обоих вариантов главной страницы настроек (`settings.xml` и `settings_legacy.xml`), открытие Developer Options / Flag Editor **без** диалога неподдерживаемого пакета, работа буфера обмена (`clipboard_content`), `GboardPatchesSettingsProvider` (порядок вкладок эмодзи/стикеров/GIF), смена языка свайпом по пробелу и курсор.
3. **Сценарий C (Режим 2 — Official package + Custom display name):**
   - `package_name = com.google.android.inputmethod.latin`, `app_display_name = Gboard Mod`.
   - Проверить: сборка проходит без ошибок маппинга, в `AndroidManifest.xml` сохранён `package="com.google.android.inputmethod.latin"`, `android:label="@string/gboard_patches_app_display_name"`, все 9 provider authorities и permissions совпадают с `com.google.android.inputmethod.latin`; установка возможна при отсутствии/удалении официальной версии (или через root-замену).
4. **Сценарий D (Негативная валидация в Morphe Manager):**
   - Ввести невалидный `package_name` (`gboard`, `com..bad`, `com.example.bad-pkg`, `com.1bad.pkg`, пустую строку) и убедиться, что Morphe Manager отклоняет значение до старта патчинга.

---

## 16. Инструкция для пользователя по публикации ветки, PR и сборке `.mpp`

Поскольку в контейнере AI Studio нет `.git` и секретов, выполните в локальном клоне `vortexnode/GboardSamsung` (на коммите `600222ff7e1ef72800c048215c364a7b48d0e0ae`):

```bash
git fetch origin
git checkout -b feat/configurable-package-name 600222ff7e1ef72800c048215c364a7b48d0e0ae
# Перенести изменённые/добавленные файлы из раздела 10
git diff --check
./gradlew :extensions:extension:testDebugUnitTest :patches:test :patches:buildAndroid
git status
git add .
git commit -m "feat(package-rename): make target Android package name configurable"
git push -u origin feat/configurable-package-name
```

После push откройте Pull Request из `feat/configurable-package-name` в `main` и дождитесь зелёного статуса GitHub Actions.

---

## 17. Подтверждение сохранности ограничений проекта

- PR #7 и PR #8 не изменялись, не закрывались и не мержились.
- Релиз `v4.3.0` и его ассеты не затрагивались.
- 12 ранее удалённых функций (включая LAN FTP) не возвращались.
- Не затрагивались Spacebar Gestures, Swipe Dismiss, переключение языков, анимация клавиатуры в WhatsApp и другие несвязанные патчи.
- Не добавлялись универсальные Morphe-патчи Clone / Update permissions / Update providers.
- Сохранены пакеты `dev.jason.gboardpatches`, значение по умолчанию `dev.jason.com.google.android.inputmethod.latin`, `GboardSamsung`, `extensions/gboard-patches.rve`, GPL-3.0 (`LICENSE`, `NOTICE`) и атрибуция Jason Wu (`UPSTREAM_COMMIT.txt`, `UPSTREAM_README.md`).

---

## 18. Итоговый статус готовности

- **Реализация кода, локализации, `patches-list.json`, автотестов и отчёта:** **100% готово**.
- **Оффлайн-верификация на JVM:** **65 / 65 тестов пройдены успешно**, побайтовая сверка `patches-list.json` пройдена, XML/JSON/diff-гигиена пройдена.
- **Ожидает выполнения в Git/CI-окружении пользователя:** создание ветки `feat/configurable-package-name`, commit/push, открытие PR, прогон GitHub Actions с `MORPHE_PACKAGES_TOKEN` (включая Robolectric и сборку `.mpp`) и ручная проверка на устройстве.
