# GboardSamsung

<p align="center">
  <strong>Возможности Samsung Keyboard на базе оригинальной Gboard</strong><br>
  Проект Morphe-патчей от Vortex Node на основе Gboard Patches Джейсона Ву.
</p>

<p align="center">
  <a href="README.md">English</a> · <a href="README.ru.md">Русский</a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue.svg" alt="GPL-3.0"></a>
  <img src="https://img.shields.io/badge/patches-34-informational" alt="34 патча">
  <img src="https://img.shields.io/badge/target-Gboard%2018.5.4-success" alt="Gboard 18.5.4">
  <img src="https://img.shields.io/badge/platform-Android-orange" alt="Android">
</p>

GboardSamsung стремится приблизить отдельные возможности и элементы взаимодействия Samsung Keyboard к **настоящему приложению Gboard**, сохранив собственный движок ввода и основные функции Gboard.

> [!IMPORTANT]
> В этом репозитории находятся **Morphe-патчи**, а не самостоятельное приложение клавиатуры. Здесь нет закрытого исходного кода или APK Google Gboard. Вам понадобится совместимый APK Gboard, который нужно самостоятельно пропатчить через Morphe Manager.

## Установка

Самый простой способ использовать GboardSamsung — добавить этот репозиторий как удалённый источник патчей в **Morphe Manager**.

### 1. Добавьте источник патчей

<p align="center">
  <a href="https://morphe.software/add-source?github=vortexnode/GboardSamsung"><strong>＋ Добавить GboardSamsung в Morphe Manager</strong></a>
</p>

Или добавьте источник вручную:

1. Откройте **Morphe Manager**.
2. Перейдите в **Patch sources → + → Remote**.
3. Вставьте адрес репозитория:

   `https://github.com/vortexnode/GboardSamsung`

### 2. Подготовьте совместимый APK Gboard

Используйте чистый одиночный APK **arm64-v8a** следующей версии:

`18.5.4.993608717-release-arm64-v8a`

Например, можно получить полный APK из надёжного архива. Не используйте split-пакеты `.apkm`, если ваш процесс патчинга не умеет корректно преобразовывать или устанавливать их.

### 3. Выберите патчи и выполните сборку

1. Откройте скачанный APK Gboard в Morphe Manager.
2. Выберите нужные патчи GboardSamsung.
3. Проверьте параметры **Package Rename**. По умолчанию используются package ID `gboardsamsung.com.google.android.inputmethod.latin` и название приложения `GboardSamsung`.
4. Запустите патчинг и установите полученный APK.

Package ID по умолчанию позволяет установить GboardSamsung рядом с официальной Gboard. Также поддерживаются официальный package ID и пользовательские идентификаторы.

### 4. Включите клавиатуру

- **Samsung One UI:** Настройки → Общие настройки → Список клавиатур и клавиатура по умолчанию. Включите **GboardSamsung** и выберите её основной клавиатурой.
- **Другие Android-устройства:** Настройки → Система → Клавиатура → Экранная клавиатура. Включите **GboardSamsung** и выберите её при необходимости.

### Обновление источника

После добавления удалённого источника Morphe Manager может проверять репозиторий на наличие обновлённых пакетов патчей и показывать новые версии после их публикации. Доступность обновлений зависит от механизма проверки источников в менеджере — обнаружение не обязательно происходит мгновенно.

## Совместимость

| Параметр | Поддерживаемая конфигурация |
|---|---|
| Gboard | `18.5.4.993608717-release-arm64-v8a` |
| Архитектура | `arm64-v8a` |
| Основное тестовое устройство | Samsung Galaxy S22 5G (`SM-S901U1`) |
| Android / One UI | Android 16 / One UI 8 |

Совместимость с другими версиями Gboard и конфигурациями устройств не гарантируется, если она не проверена отдельно.

## Каталог патчей

Сейчас GboardSamsung включает **34 активных Morphe-патча**: 33 выбранных upstream-патча и один собственный downstream-патч для жестов на пробеле в стиле Samsung.

### Взаимодействие в стиле Samsung

- **Samsung Spacebar Gestures** — свайп влево или вправо по пробелу переключает активные языки; короткое нажатие по-прежнему вводит пробел, а удержание с перетаскиванием перемещает курсор.

### Темы, внешний вид и раскладка · 12

- **Custom Theme** — импорт пользовательских ZIP-тем, в том числе из репозитория Rboard.
- **Frosted Glass** — размытие фона в реальном времени и настройка прозрачности пользовательской темы.
- **Rounded Keyboard Panel** — выбор скругляемых углов панели и настройка радиусов.
- **Change emoji size** — изменение размера эмодзи.
- **Top Toolbar Item Count** — настройка количества элементов верхней панели.
- **Access Points menu style** — переключение между новым и классическим стилями меню Access Points.
- **Enable split keyboard** — включение разделённой раскладки.
- **Key Shape Selection** — настройка формы клавиш в параметрах темы.
- **Custom Symbols** — отдельная вкладка специальных символов и быстрый доступ к ней долгим нажатием на запятую.
- **Emojis, stickers & GIFs Tab Order** — изменение порядка нижних вкладок панели эмодзи, стикеров и GIF.
- **Settings Homepage Override** — переключение между новой и классической главными страницами настроек Gboard.
- **Close Proactive Suggestions** — кнопка закрытия панели проактивных подсказок.

### Ввод, редактирование и инструменты · 10

- **Long-Press Editing Shortcuts** — быстрые действия при долгом нажатии: «Выбрать всё», «Отменить», «Копировать», «Вырезать», «Вставить» и «Повторить».
- **Toolbar Editing Buttons** — добавление команд «Выбрать всё», «Копировать», «Вырезать» и «Вставить» в меню панели инструментов.
- **Quick Insert** — включение панели быстрой вставки и её значка на панели инструментов.
- **Swipe Down to Dismiss Keyboard** — закрытие клавиатуры свайпом вниз от подсказок или панели инструментов.
- **Keypress Vibration Intensity Control** — настройка интенсивности вибрации при нажатии.
- **Floating Web Search** — открытие плавающей веб-страницы для быстрого поиска.
- **Simple Calculator** — отображение выражения и результата в строке подсказок.
- **Incognito Mode Toggle** — переключатель режима инкогнито и настройка доступности буфера обмена и голосового ввода в этом режиме.
- **Clipboard Enhancements** — настройка срока хранения, лимита записей, предпросмотра, меток времени, порядка и числа столбцов буфера обмена.
- **Clipboard Custom Character Limit** — ограничение максимального количества символов в каждой текстовой записи буфера обмена.

### Языки, ИИ, подсказки и голос · 7

- **AI Writing Tools** — включение инструментов ИИ для работы с текстом на поддерживаемых языках.
- **Advanced Voice Typing** — включение расширенного голосового ввода и связанных параметров пунктуации.
- **Grammar Checker** — включение проверки грамматики.
- **Inline Suggestions** — включение функции Smart Compose.
- **Enable Inline Autofill Suggestions** — включение встроенных подсказок автозаполнения.
- **Use Bluetooth Microphone** — доступ к Bluetooth-микрофону при голосовом вводе.
- **Dictionaries & Predictions** — выбор версий ресурсов словарей, предсказаний и исправления ввода.

### Сборка, безопасность и утилиты · 4

- **Package Rename** — настройка Android package ID и отображаемого имени приложения. Значения по умолчанию: `gboardsamsung.com.google.android.inputmethod.latin` и `GboardSamsung`.
- **Add Gboard Signature Bypass** — обход проверки Gboard по списку разрешённых подписей.
- **Backup & Restore** — экспорт и восстановление настроек патчей, резервное копирование, сравнение и восстановление PB/XML-данных Gboard.
- **Developer options** — включение параметров разработчика Gboard и редактора флагов.

### Upstream-патчи, которые не входят в проект

Из downstream-проекта были удалены 12 upstream-функций, чтобы сохранить чёткие границы проекта и неиспользуемые зависимости.

<details>
<summary>Показать список 12 исключённых патчей</summary>

1. Latin Globe Key Ignore Interval
2. Web Clipboard
3. Zhuyin Bottom Row Key Sizes
4. Zhuyin Quick Traditional/Simplified Toggle
5. Zhuyin Slide Input
6. G Logo on Spacebar
7. English QWERTY Up-Flick Uppercase
8. FTP Server
9. Swipeable Custom Top Row
10. Enable OCR / Scan Text
11. Enable accessibility layout
12. Enable cursor trackpad mode

Удаление этих патчей не затронуло общую функциональность, которая продолжает использоваться GboardSamsung. Например, стандартный буфер обмена Gboard и собственный патч Samsung Spacebar Gestures сохранены.

</details>

## Частые вопросы

<details>
<summary><strong>GboardSamsung — это отдельная клавиатура?</strong></summary>

Нет. Это набор Morphe-патчей, применяемых к совместимому APK Gboard. Пропатченное приложение может использовать отдельный package ID и устанавливаться рядом с официальной Gboard.
</details>

<details>
<summary><strong>Нужно ли устанавливать Morphe Manager?</strong></summary>

Да. Через Morphe Manager добавляется источник патчей, выбираются нужные функции, выполняется патчинг APK Gboard и установка результата.
</details>

<details>
<summary><strong>Подойдёт любая версия Gboard?</strong></summary>

Нет, такой совместимости мы не обещаем. Документированная целевая версия — `18.5.4.993608717-release-arm64-v8a`.
</details>

<details>
<summary><strong>После добавления источника клавиатура обновляется автоматически?</strong></summary>

Нет. Morphe Manager может проверять наличие новых пакетов патчей в удалённом источнике. Чтобы обновить установленную клавиатуру, вам всё равно потребуется выбрать и применить патчи к совместимому APK Gboard.
</details>

## Сборка и проверка

Репозиторий собирает **пакеты Morphe (`.mpp`)** и метаданные каталога патчей. Самостоятельный APK клавиатуры здесь не собирается.

Основные команды разработчика:

```bash
./gradlew :patches:buildAndroid
./gradlew generatePatchesList
./gradlew test
```

CI запускает модульные тесты, проверяет актуальность `patches-list.json` и собирает Morphe Android patch bundle. После успешного CI для push в `main` workflow публикации анализирует коммиты и выпускает релиз источника Morphe.

Правила классификации релизов задаются в [`.releaserc`](.releaserc). Сейчас breaking changes дают major-релиз, `feat:` — minor, а `fix:`, `bump:`, `perf:`, `build(morphe-source):` и `docs:` — patch-релиз. Документация скрыта в автоматически сформированных разделах release notes, однако **текущая конфигурация всё равно классифицирует коммиты `docs:` как основание для patch-релиза**.

Для доступа к GitHub Packages Morphe требуются `gpr.user` / `gpr.key` или соответствующие учётные данные GitHub Actions. Никогда не добавляйте в репозиторий пароли, ключи подписи, токены или проприетарные APK Gboard.

## Происхождение проекта

- **Автор upstream:** Jason Wu (`jasonwu1994`) — [Gboard Patches](https://github.com/jasonwu1994/Gboard-patches)
- **Downstream-разработчик:** Vortex Node (`vortexnode`) — [GboardSamsung](https://github.com/vortexnode/GboardSamsung)
- **Импортированная базовая версия upstream:** `463269a0f315ef9cee749e10d155af1aedaa49ad` (зафиксирована в [`UPSTREAM_COMMIT.txt`](UPSTREAM_COMMIT.txt))
- **Фреймворк патчей:** [Morphe](https://github.com/MorpheApp)
- **Лицензия:** [GNU GPL v3.0](LICENSE); также см. [`NOTICE`](NOTICE)

Upstream-репозиторий указан как исторический источник выбранных патчей. GboardSamsung развивается независимо и не зависит от действующего процесса автоматического импорта upstream.

## Документация

- [Границы проекта](docs/PROJECT_SCOPE.md) — цели, ограничения и критерии приёмки.
- [Происхождение upstream](docs/UPSTREAM.md) — история, отличия и правила синхронизации.
- [Отчёт об очистке репозитория](docs/REPOSITORY_CLEANUP_REPORT.md) — удалённые компоненты и проверка каталога.
- [Архивный README upstream](UPSTREAM_README.md) — оригинальная документация проекта Gboard Patches.
