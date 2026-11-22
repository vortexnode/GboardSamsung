# GboardSamsung

**[English](README.md) | [Русский](README.ru.md)**

**GboardSamsung** — это форк-проект Morphe-патчей от **Vortex Node**, основанный на [Gboard Patches](https://github.com/jasonwu1994/Gboard-patches) Джейсона Ву (`GPL-3.0`). Цель проекта — максимально приблизить опыт использования оригинальной Gboard к клавиатуре Samsung (включая двустороннее переключение языков свайпом по пробелу с сохранением обычного нажатия и перемещения курсора удержанием), полностью сохранив движок ввода Gboard, подсказки, словари, голосовой ввод, буфер обмена и остальные ключевые функции.

> **Важно:** В этом репозитории находятся только Morphe-патчи для вашего APK-файла Gboard. Это **не** отдельное приложение клавиатуры, и здесь **нет** закрытого исходного кода или бинарников Google Gboard.

## Поддерживаемая версия

- **Целевой билд Gboard:** `18.5.4.993608717-release-arm64-v8a` (`18.5.4`)
- **Основное тестовое устройство:** Samsung Galaxy S22 5G (`SM-S901U1`), Android 16 / One UI 8

## Установка (через Morphe Manager)

GboardSamsung распространяется в виде патчей для Morphe, а не готового APK. Вы сами патчите оригинальный Gboard прямо на телефоне через **Morphe Manager**:

### 1. Добавьте источник патчей
- **В одно нажатие:** [Добавить GboardSamsung в Morphe Manager](https://morphe.software/add-source?github=vortexnode/GboardSamsung)
- **Вручную:** Откройте Morphe Manager → **Patch sources → + → Remote** и вставьте ссылку:
  ```text
  https://github.com/vortexnode/GboardSamsung
  ```

### 2. Скачайте нужный APK Gboard
- Скачайте чистый одиночный **arm64-v8a** APK Gboard версии **`18.5.4.993608717-release-arm64-v8a`** (например, с APKMirror — нужен именно целый `.apk`, а не split-бандл `.apkm`, если менеджер не распаковывает его автоматически).

### 3. Пропатчите и установите
1. В Morphe Manager выберите скачанный APK Gboard `18.5.4`.
2. Отметьте нужные патчи (по умолчанию патч `Package Rename` меняет package ID на `gboardsamsung.com.google.android.inputmethod.latin` и имя на `GboardSamsung`, поэтому клавиатуру можно поставить рядом со стандартной Gboard без конфликтов).
3. Запустите сборку и установите готовый APK.

### 4. Включите клавиатуру в системе
- **Samsung (One UI):** *Настройки → Общие настройки → Список клавиатур и клавиатура по умолчанию* → включите **GboardSamsung** и выберите её основной.
- **Другие Android-устройства:** *Настройки → Система → Клавиатура → Экранная клавиатура* → включите **GboardSamsung**.

> **Обновления:** После добавления удалённого источника Morphe Manager будет автоматически проверять появление новых версий пакета патчей в этом репозитории.

## Происхождение и лицензия

- **Оригинальный автор (upstream):** Jason Wu (`jasonwu1994`) — [jasonwu1994/Gboard-patches](https://github.com/jasonwu1994/Gboard-patches)
- **Модификация и адаптация (downstream):** `vortexnode` (Vortex Node) — [vortexnode/GboardSamsung](https://github.com/vortexnode/GboardSamsung)
- **Базовый коммит upstream:** `463269a0f315ef9cee749e10d155af1aedaa49ad` (`2026-11-12`, зафиксирован в `UPSTREAM_COMMIT.txt`; оригинальный README сохранён в `UPSTREAM_README.md`)
- **Фреймворк патчей:** Morphe (`app.morphe.patches`)
- **Лицензия:** GNU General Public License v3.0 (`LICENSE`, `NOTICE`)

## Каталог активных патчей (34 патча)

В GboardSamsung включены **34** публичных Morphe-патча (33 отобранных патча из upstream и фирменный патч `Samsung Spacebar Gestures`):

- **Опыт Samsung (1):**
  - `Samsung Spacebar Gestures` — свайп влево/вправо по пробелу для циклического переключения языков с сохранением короткого нажатия для пробела и удержания (~300 мс) + перетаскивания для управления курсором (независимо от `Swipe Down to Dismiss Keyboard`).
- **Темы, внешний вид и раскладка (12):**
  - `Custom Theme`, `Frosted Glass`, `Rounded Keyboard Panel`, `Change emoji size`, `Top Toolbar Item Count`, `Access Points menu style`, `Enable split keyboard`, `Key Shape Selection`, `Custom Symbols`, `Emojis, stickers & GIFs Tab Order`, `Settings Homepage Override`, `Close Proactive Suggestions`.
- **Ввод, редактирование и инструменты (10):**
  - `Long-Press Editing Shortcuts`, `Toolbar Editing Buttons`, `Quick Insert`, `Swipe Down to Dismiss Keyboard`, `Keypress Vibration Intensity Control`, `Floating Web Search`, `Simple Calculator`, `Incognito Mode Toggle`, `Clipboard Enhancements`, `Clipboard Custom Character Limit`.
- **Языки, ИИ, подсказки и голос (7):**
  - `AI Writing Tools`, `Advanced Voice Typing`, `Grammar Checker`, `Inline Suggestions`, `Enable Inline Autofill Suggestions`, `Use Bluetooth Microphone`, `Dictionaries & Predictions`.
- **Сборка, безопасность и утилиты (4):**
  - `Package Rename` (по умолчанию package ID: `gboardsamsung.com.google.android.inputmethod.latin`, отображаемое имя: `GboardSamsung`; также поддерживаются официальный `com.google.android.inputmethod.latin` и любые свои ID), `Add Gboard Signature Bypass`, `Backup & Restore`, `Developer options`.

### Удалённые функции из upstream (12)

Чтобы не перегружать код и зависимости (Apache FTPServer/MINA, QuickJS, специфику Zhuyin, логотип G и т.д.), 12 патчей из оригинального репозитория были убраны:

1. `Latin Globe Key Ignore Interval`
2. `Web Clipboard` (стандартный буфер обмена Gboard, `Custom Clipboard Enhancements` и `Clipboard Custom Character Limit` сохранены)
3. `Zhuyin Bottom Row Key Sizes`
4. `Zhuyin Quick Traditional/Simplified Toggle`
5. `Zhuyin Slide Input` (`Show custom symbols` сохранён)
6. `G Logo on Spacebar` (`Samsung Spacebar Gestures` сохранён)
7. `English QWERTY Up-Flick Uppercase`
8. `FTP Server`
9. `Swipeable Custom Top Row`
10. `Enable OCR / Scan Text`
11. `Enable accessibility layout`
12. `Enable cursor trackpad mode` (управление курсором с пробела сохранено в `Samsung Spacebar Gestures`)

## Сборка для разработчиков

Проект собирает пакет патчей Morphe (`.mpp`) и метаданные каталога (`patches-list.json`, `patches-bundle.json`), а не самостоятельный APK:

- Собрать пакет патчей: `./gradlew :patches:buildAndroid`
- Обновить метаданные патчей: `./gradlew generatePatchesList`
- Запустить юнит-тесты: `./gradlew test`
- Для загрузки зависимостей Morphe из GitHub Packages требуются `gpr.user` / `gpr.key` (или `GITHUB_ACTOR` / `GITHUB_TOKEN`). Никогда не коммитьте токены, ключи и проприетарные APK.

Изменения, влитые в `main`, сначала проходят тесты; коммиты с префиксами `fix:`, `feat:`, `bump:`, `perf:` и `build(morphe-source):` автоматически публикуют новый релиз с `.mpp`-бандлом. Коммиты только с тестами или документацией новый релиз не создают.

## Документация

- [docs/PROJECT_SCOPE.md](docs/PROJECT_SCOPE.md) — границы проекта, инженерные правила и критерии приёмки
- [docs/UPSTREAM.md](docs/UPSTREAM.md) — происхождение и правила синхронизации с upstream
- [docs/REPOSITORY_CLEANUP_REPORT.md](docs/REPOSITORY_CLEANUP_REPORT.md) — отчёт об очистке репозитория и синхронизации каталога
- [UPSTREAM_README.md](UPSTREAM_README.md) — оригинальная документация upstream-проекта Gboard Patches
