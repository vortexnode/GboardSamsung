# Этап 4.1 — глубокая трассировка Free Cursor и двунаправленного переключения языков в Gboard `18.5.4`

**Репозиторий:** `vortexnode/GboardSamsung` (ветка `main`)  
**Целевая версия Gboard:** `18.5.4.993608717-release-arm64-v8a` (`SHA-256: f47f44d33ab21d28aabfaec8292009c1173964ea238441734327a0a15ba32813`)  
**Тип этапа:** исключительно исследовательский (без изменения исходного кода, ABI, bindings, Gradle, registry, wiring, workflows и без сборки `.mpp`).

---

## 1. Статус исходных артефактов и границы верификации в текущей среде

### 1.1. Что было доступно и проверено напрямую
1. **Бинарные APK/DEX Gboard `18.5.4` в текущей контейнерной среде отсутствуют** (`find . /public/assets/aistudio /tmp -maxdepth 4 \( -name "*.apk" -o -name "*.dex" -o -name "*.smali" \)` вернул 0 файлов). Поэтому живая декомпиляция `classes.dex`–`classes4.dex` через JADX или Baksmali в данном контейнере технически невыполнима.
2. **Использованные авторитетные источники (прочитаны напрямую):**
   - `docs/GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md` (прямое извлечение дескрипторов классов/методов и string/method-ссылок из `classes.dex`–`classes4.dex` целевого APK);
   - `docs/SAMSUNG_KEYBOARD_5.9.50.10_BYTECODE_REFERENCE_EXTRACT.md` (эталонные ссылки `classes2.dex` Samsung Keyboard `5.9.50.10`);
   - `docs/SPACEBAR_LANGUAGE_SWITCH_DESIGN.md` и `docs/TECHNICAL_AUDIT.md`;
   - реальные исходники и подтверждённые биндинги `vortexnode/GboardSamsung` для Gboard `18.5.4`:
     - `patches/src/main/resources/gboard/gboard-version-bindings.json` (`profile_version: 1.6.0`, `target_version: 18.5.4`);
     - `GboardCursorTrackpadPreferencePatch.kt`, `GboardCursorTrackpadFlagValuePatch.kt`, `GboardCursorTrackpadPolicy.java`, `GboardCursorTrackpadRuntime.java`, `GboardCursorTrackpadSettingsFeature.java`, `GboardCursorTrackpadPreferenceTest.java`;
     - `GboardLongPressQuickActionsReflectionHandles.java`, `GboardLongPressQuickActionsRuntime.java`, `GboardLongPressQuickActionsInputEventPatch.kt`;
     - `GboardGlobeDragAdapter.java`, `GboardGlobeDragRuntime.java`, `GboardLatinGlobeKeyIgnoreIntervalBytecodePatch.kt`;
     - `GboardSwipeDismissBytecodePatch.kt`, `GboardSwipeDismissRuntime.java`;
     - `GboardPointerOwnerTransformation.kt`, `GboardGestureFamilyComposer.kt`, `GboardSoftKeyFamilyComposer.kt`, `GboardFlagFamilyComposer.kt`;
     - `GboardSpacebarLogoAdapter.java`, `GboardSpacebarLogoRuntime.java`, `GboardEditorActions.java`, `RuntimeAbi.kt`.
3. **Git-среда:** в контейнере отсутствует каталог `.git` (`ls .git` → `No such file or directory`), поэтому локальный `git commit` / `git push` из контейнера невозможен. При этом через GitHub API проверено актуальное содержимое `docs/` в ветке `main` (`vortexnode/GboardSamsung`) — состав из 8 документов полностью совпал с локальной копией.

### 1.2. Строгий перечень того, что НЕВОЗМОЖНО доказать без полной декомпиляции APK `18.5.4`
Без JADX/Baksmali-дизассемблирования `Gboard-v18.5.4.993608717-release-arm64-v8a.apk` невозможно доказать на уровне инструкций (control-flow):
- полные тела и ветвления `FreeCursorKeyboard` (`e`, `eR`, `m`, `h`, `P`), `FreeCursorMotionEventHandler`, `TriggerFreeCursorMotionEventHandler` и места использования всех констант `Lxfe;`;
- точный возвращаемый тип и условия вызова методов `FreeCursorKeyboard`, `ScrubMotionEventHandler`, `ScrubMoveMotionEventHandler`, `ScrubDeleteMotionEventHandler`;
- зависимость `TriggerFreeCursorMotionEventHandler` и `ScrubDeleteMotionEventHandler` от `"enable_scrub_move"` / `0x7f14096b`;
- ветвления `Lqin;->y()V` и точный таймаут `space_long_press_ime_picker`;
- обработчик `Lqga.SWITCH_TO_NEXT_LANGUAGE` / `-0x271b`, структуру и обфусцированные дескрипторы менеджера `InputMethodEntry` (получение списка, текущего элемента и переключение по индексу/элементу, наличие шага `-1` или MRU-сортировки);
- декодированные значения XML/arsc-ресурсов порогов Free Cursor / Scrub.

Все утверждения ниже строго разделяют **непосредственно доказанное** и **гипотезы / блокеры**.

---

## 2. Исследование A — встроенный Free Cursor в Gboard `18.5.4`

### 2.1. Сводка подтверждённых дескрипторов и байткод-ссылок

| Артефакт / DEX / исходник | Класс / тип | Подтверждённый descriptor / сигнатура | Конкретные вызовы, строки, enum | Что доказано непосредственно | Что остаётся гипотезой / не доказано |
|---|---|---|---|---|---|
| `GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md` (DEX extract) | `Lcom/google/android/libraries/inputmethod/freecursor/FreeCursorKeyboard;` | Методы `e(Landroid/view/inputmethod/EditorInfo;Ljava/lang/Object;)` (return type в extract не указан), `eR(Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;Lcom/google/android/libraries/inputmethod/metadata/KeyboardViewDef;)`, `m(Lohe;)`, `h()`, `P(Lohe;)` | Строки в `e(...)`: `"free_cursor_activation_source"`, `"free_cursor_activation_swipe_direction"`; конструирование объекта с enum источника Free Cursor; `eR` привязывает click listeners к view; `m(Lohe)` обрабатывает внутренние события клавиатуры; `h()` меняет видимость и эмитит события; `P(Lohe)` читает состояние текста/выделения редактора. | В APK `18.5.4` существует полноценный класс клавиатуры Free Cursor, принимающий `EditorInfo` + payload аргумент с ключами `free_cursor_activation_source` и `free_cursor_activation_swipe_direction` и реагирующий на `Lohe;`. | Точные возвращаемые типы методов, условия открытия/закрытия клавиатуры, вызывается ли `InputConnection.sendKeyEvent` или `setSelection`, и можно ли запускать `FreeCursorKeyboard` программно напрямую. |
| `GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md` (`classes3.dex`) | `Lxfe;` | Enum-тип (точные дескрипторы полей/методов в extract не приведены) | Константы: `FREE_CURSOR_SOURCE_DIRECT_KEYCODE`, `FREE_CURSOR_SOURCE_SPACE_LONG_PRESS_MULTI_KEYBOARD`, `FREE_CURSOR_SOURCE_SPACE_LONG_PRESS_SINGLE_KEYBOARD`, `FREE_CURSOR_SOURCE_SWIPE_DEL`, `FREE_CURSOR_SOURCE_SWIPE_DPAD`, `FREE_CURSOR_SOURCE_SWIPE_SPACE`, `FREE_CURSOR_SOURCE_UNSPECIFIED`. | Сборка `18.5.4` явно различает активацию Free Cursor через long-press пробела (single/multi keyboard), swipe по пробелу, swipe по Del, swipe по D-pad и прямой keycode. | Какие именно классы конструируют/читают каждую из этих констант, какая константа включена по умолчанию на `SM-S901U1`, и какой keycode соответствует `FREE_CURSOR_SOURCE_DIRECT_KEYCODE`. |
| `GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md` (DEX extract) | `Lcom/google/android/libraries/inputmethod/freecursor/FreeCursorMotionEventHandler;` и `TriggerFreeCursorMotionEventHandler;` | Классы подтверждены; полные дескрипторы методов в extract не раскрыты | Наличие двух отдельных `MotionEventHandler`: триггер (`TriggerFreeCursorMotionEventHandler`) и активный обработчик (`FreeCursorMotionEventHandler`). | В Gboard `18.5.4` разделены фаза триггера входа в Free Cursor и фаза обработки касаний внутри активного Free Cursor. | Точная логика перехвата `MotionEvent`, владение указателем после активации, завершение на `ACTION_UP`/`ACTION_CANCEL`, условия фильтрации `FREE_CURSOR_SOURCE_SWIPE_SPACE`. |
| `gboard-version-bindings.json` + `GboardCursorTrackpadPolicy.java:4-5` + `GboardCursorTrackpadPreferencePatch.kt:24-43` + `GboardCursorTrackpadRuntime.java:11-37` | `Loki;`, `Lqvt;`, `Lcht;` | `Loki;->g()Ljava/lang/Object;`, `Loki;->a:Ljava/lang/String;`; `Lqvt;->am(Ljava/lang/String;ZZ)Z`; `Lcht;->x(IZ)Z` | Флаги `"free_cursor"`, `"free_cursor_lock_mode"`; строковая настройка `"enable_scrub_move"`; строковый ресурс-ключ `0x7f14096b`. В `gboard_settings_text.xml:2200,2344` фича описана как «Long-press the space bar to open a trackpad ... with a lock mode». | В `GboardSamsung` уже подтверждены точные точки управления флагами `free_cursor`/`free_cursor_lock_mode` и настройкой `enable_scrub_move` (`0x7f14096b`). **Критическое наблюдение из кода репо:** штатный патч `GboardCursorTrackpad` форсирует **одновременно** `free_cursor = true`, `free_cursor_lock_mode = true` и `enable_scrub_move / 0x7f14096b = true`. | Не доказано, является ли `enable_scrub_move = true` жёстким предварительным условием для работы `TriggerFreeCursorMotionEventHandler`, или только для включения UI-переключателя/горизонтального scrub. |
| `GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md` (DEX extract) | `ScrubMotionEventHandler`, `ScrubMoveMotionEventHandler`, `ScrubDeleteMotionEventHandler` | `ScrubMotionEventHandler`: `g(MotionEvent)`, `p(MotionEvent, int)`, `o(int, float, float)`, `r(MotionEvent, boolean)`, `b(MotionEvent)`, `t(MotionEvent)`, `c()` | `g(MotionEvent)` вызывает `SoftKeyboardView.A(MotionEvent, int)`, `SoftKeyView.f(qae)`, `ActionDef.b()`; `p` читает `getHistorySize/getHistoricalX/Y`; `o` вызывает `Rect.contains`, `Math.abs`, `c()`; `r` ссылается на `"noScrubbing"`; конструктор читает integer/dimension ресурсы. | Горизонтальный scrub-курсор (`ScrubMoveMotionEventHandler`) и свайп-удаление с Backspace (`ScrubDeleteMotionEventHandler`) являются **раздельными подклассами** `ScrubMotionEventHandler`. Scrub уважает `EditorInfo` опцию `"noScrubbing"`. | Не доказано, зависит ли `ScrubDeleteMotionEventHandler` от `enable_scrub_move` / `0x7f14096b` и вызывает ли `ScrubMoveMotionEventHandler` переход в `FreeCursorKeyboard` (`FREE_CURSOR_SOURCE_SWIPE_SPACE`). |
| `gboard-version-bindings.json` + `GboardLongPressQuickActionsReflectionHandles.java:85-86,114,292-306,361` | `Lqin;` (pointer tracker) | `Lqin;->B(SoftKeyView,FFJI)V`, `r(JI)V`, `s(J)V`, `C()V`, `ac()V`, `Lqin;->y()V`, поле `Lqin;->A:Ljava/util/concurrent/Future;` | `y()` планирует `Future` долгого нажатия в поле `A`; `s(J)V` отменяет указатель; в DEX extract присутствует строка `"space_long_press_ime_picker"`. | Подтверждены точные дескрипторы планирования и отмены `longPressFuture` (`Lqin;->y()V` и `Lqin;->A:Ljava/util/concurrent/Future;`). | Не доказаны тело `Lqin;->y()V` (источник таймаута при настройке *Short*) и точное место обработки `"space_long_press_ime_picker"` в `18.5.4`. |

### 2.2. Ответы на 12 обязательных вопросов Исследования A

1. **Какой класс фактически принимает решение об активации Free Cursor?**
   - *Частично подтверждено / гипотеза:* по иерархии классов из `GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md` главным кандидатом является `TriggerFreeCursorMotionEventHandler` (в паре с `FreeCursorKeyboard.e(EditorInfo, Object)`), однако без декомпиляции тела `TriggerFreeCursorMotionEventHandler` и поиска всех call sites `Lxfe;` это **не доказано на уровне control-flow**.
2. **Как long-press пробела преобразуется в `FREE_CURSOR_SOURCE_SPACE_LONG_PRESS_SINGLE_KEYBOARD` или `MULTI_KEYBOARD`?**
   - *Не доказано (`BLOCKED`):* наличие обеих констант в `Lxfe;` (`classes3.dex`) подтверждено, но call sites, формирующие эти значения (и передающие их в `FreeCursorKeyboard.e(EditorInfo, Object)` под ключом `free_cursor_activation_source`), отсутствуют в DEX reference extract.
3. **Можно ли активировать Free Cursor программно из нашего runtime, не имитируя касания и не включая `FREE_CURSOR_SOURCE_SWIPE_SPACE`?**
   - *Не доказано (`BLOCKED`):* косвенным указанием на такую возможность является константа `Lxfe.FREE_CURSOR_SOURCE_DIRECT_KEYCODE`, но конкретный целочисленный keycode, принимающий его метод и передача владения текущим `MotionEvent` после программного открытия `FreeCursorKeyboard` не подтверждены байткодом.
4. **Существует ли публичный или внутренний метод/интерфейс для запуска Free Cursor? Точный descriptor и сигнатура.**
   - *Не подтверждён (`BLOCKED`):* известна только внутренняя точка активации клавиатуры `FreeCursorKeyboard.e(Landroid/view/inputmethod/EditorInfo;Ljava/lang/Object;)` (без подтверждённого возвращаемого типа и без подтверждённого фабричного/менеджерского вызова, который переключает активный `Keyboard` на `FreeCursorKeyboard`). Точный метод запуска **не выдумывается**.
5. **Кто становится владельцем `MotionEvent` после активации?**
   - *Не доказано (`BLOCKED`):* в APK подтверждён отдельный `FreeCursorMotionEventHandler`, но порядок передачи уже начатого жеста (`ACTION_DOWN` на пробеле → переход в `FreeCursorKeyboard` → последующие `ACTION_MOVE` / `ACTION_UP`) от `TriggerFreeCursorMotionEventHandler` к `FreeCursorMotionEventHandler` требует декомпиляции обоих классов.
6. **Как Free Cursor завершает работу при `ACTION_UP`, `ACTION_CANCEL`, смене окна и завершении input session?**
   - *Не доказано (`BLOCKED`):* в `FreeCursorKeyboard` видны `h()` (видимость и внутренние события) и `m(Lohe)`, а на `Lphr;` подтверждены `onWindowHidden()V`, `onFinishInputView(Z)V`, `onFinishInput()V`, `onDestroy()V` (`gboard-version-bindings.json`), но точная логика завершения `FreeCursorMotionEventHandler` на `ACTION_UP` / `ACTION_CANCEL` без APK не прослежена.
7. **Вставляется ли обычный пробел после отпускания пальца?**
   - *Не доказано на уровне байткода Free Cursor (`BLOCKED`), но архитектурно контролируемо:* при использовании нашего единого `GboardTouchDispatchFamilyComposer` + страховочной стадии в `GboardGestureFamilyComposer` (`Lqil;->f` для `keyCode == 62`) утечка `KEYCODE_SPACE` гарантированно подавляется на уровне диспетчера независимо от того, как ведёт себя `TriggerFreeCursorMotionEventHandler`. Для штатного пути без нашего `ACTION_CANCEL` поведение должно быть проверено по телу `TriggerFreeCursorMotionEventHandler`.
8. **Можно ли гарантированно исключить вызов `space_long_press_ime_picker`?**
   - *Частично подтверждено:* на уровне нашего диспетчера — **да** (отмена через синтетический `ACTION_CANCEL` в `KeyboardViewHolder` вызывает `Lqin;->s(J)V`, отменяющий `Lqin;->A:Ljava/util/concurrent/Future;`, плюс страховочное подавление `LONG_PRESS` для `keyCode == 62` в `Lqil;->f`). Как именно сам `TriggerFreeCursorMotionEventHandler` подавляет `space_long_press_ime_picker` внутри Gboard, без декомпиляции не доказано.
9. **Требуется ли `free_cursor_lock_mode` или режим может работать только пока палец удерживается?**
   - *Не доказано (`BLOCKED`):* в `GboardCursorTrackpadPolicy.java:4-5` подтверждены два **раздельных** флага `"free_cursor"` и `"free_cursor_lock_mode"`. Это указывает на потенциальную независимость базового Free Cursor от lock mode, но точное поведение при `free_cursor = true, free_cursor_lock_mode = false` (закрывается ли сразу на `ACTION_UP`) требует проверки кода `FreeCursorKeyboard` / `FreeCursorMotionEventHandler`.
10. **Можно ли заблокировать `FREE_CURSOR_SOURCE_SWIPE_SPACE`, сохранив активацию Free Cursor через long-press?**
    - *Не доказано (`BLOCKED`):* архитектурно `GboardTouchDispatchFamilyComposer` способен перехватывать горизонтальный свайп по пробелу раньше `TriggerFreeCursorMotionEventHandler` и `ScrubMoveMotionEventHandler`, но неизвестно, не запускается ли `FREE_CURSOR_SOURCE_SWIPE_SPACE` по флагу/ветке, неразрывно связанной с `enable_scrub_move` или `TriggerFreeCursorMotionEventHandler`.
11. **Как связаны Free Cursor и Scrub Move? Можно ли отключить только горизонтальный scrub, не затронув Scrub Delete?**
    - *Частично подтверждено:*
      - **Подтверждено в DEX extract:** `ScrubMoveMotionEventHandler` и `ScrubDeleteMotionEventHandler` — два отдельных подкласса `ScrubMotionEventHandler`.
      - **Подтверждено в исходниках репо (`GboardCursorTrackpadRuntime.java:11-37`):** существующий патч `GboardCursorTrackpad` форсирует `"enable_scrub_move"` (`Lqvt;->am`) и `0x7f14096b` (`Lcht;->x`) в `true` вместе с `"free_cursor"`.
      - **Не доказано (`BLOCKED`):** зависит ли `FreeCursor` от `enable_scrub_move = true`, и читает ли `ScrubDeleteMotionEventHandler` ключ `"enable_scrub_move"` / `0x7f14096b` или отдельный ключ (`enable_scrub_delete`). **Поэтому отключать общий флаг `enable_scrub_move` / `0x7f14096b` до проверки байткода запрещено.**
12. **Как Free Cursor взаимодействует с composing text, inline suggestions и Glide Typing?**
    - *Не доказано (`BLOCKED`):* в `GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md` подтверждено лишь то, что `FreeCursorKeyboard.P(Lohe)` читает состояние текста/выделения редактора, а в DEX присутствуют `INLINE_SUGGESTION_SELECT_BY_SWIPE_ON_SPACEBAR` и `Lphr.dF(...)` (`finishComposingText()`). Связь `FreeCursorKeyboard` с `Lphr.dF(...)`, `Lpdd.i(...)` (`setSelection`) и `InputConnection.sendKeyEvent` не прослежена.

---

## 3. Исследование B — переключение языков `NEXT` / `PREVIOUS` в Gboard `18.5.4`

### 3.1. Сводка подтверждённых дескрипторов и байткод-ссылок

| Артефакт / исходник | Класс / тип | Подтверждённый descriptor / константа | Подтверждённые факты | Что НЕ доказано без APK `18.5.4` |
|---|---|---|---|---|
| `gboard-version-bindings.json` + `GboardLongPressQuickActionsReflectionHandles.java:84,121-123,226-229` + `GboardGlobeDragAdapter.java:10` | `Lphr;`, `Lohe;`, `Lqbc;`, `Lqae;` | `Lphr;->aw(Lohe;)Z` (`public final boolean` на подклассе `InputMethodService`); `Lohe;->a:Lqae;`, `b:[Lqbc;`, `c:SoftKeyDef;`, `w:I`; `Lqbc;->c:I`, `e:Object;`; keycode `-0x271b` (`-10011`), payload `"globe"`, `qae.PRESS`; keycode `-0x2726` (`qae.LONG_PRESS`, IME picker) | Входная точка обработки событий клавиш IME (`Lphr;->aw(Lohe;)Z`) и формат события переключения на следующий язык (`-0x271b`, `"globe"`) полностью подтверждены. | Тело `Lphr;->aw(Lohe;)Z` и вызываемый далее диспетчер `-0x271b` не видны без APK. |
| `GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md` | `Lqga;` | Enum-константы `SWITCH_LANGUAGE`, `SWITCH_TO_NEXT_LANGUAGE` | Подтверждены оба имени действия в enum `Lqga;`, а также строки `Input.switchToNextLanguage.%s`, `PREF_SHOW_LANGUAGE_SWITCH_KEY`. | Неизвестны остальные значения `Lqga;`, обработчик `SWITCH_LANGUAGE` (принимает ли целевой `InputMethodEntry` или направление) и точный маппинг `-0x271b` → `Lqga`. |
| `GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md` | `LanguageSwitcherAccessPointProvider`, `InputMethodManagerWrapper` | Метод `InputMethodManagerWrapper.hasOtherSwitchableImes` (точная сигнатура параметров/возврата в extract не указана) | Подтверждено наличие провайдера access point переключения языков и обёртки над `InputMethodManager` с проверкой сторонних IME. | Дескрипторы методов внутри `LanguageSwitcherAccessPointProvider` и класс менеджера `InputMethodEntry` не раскрыты в extract. |
| `GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md` + `grep` по всему `vortexnode/GboardSamsung` | 4 DEX string tables + весь репозиторий | `SWITCH_TO_PREVIOUS_LANGUAGE` — **NOT FOUND** | Буквальной строки `SWITCH_TO_PREVIOUS_LANGUAGE` нет ни в одном из 4 DEX-файлов `18.5.4`, и в исходниках/биндингах `GboardSamsung` нет ни одного дескриптора `InputMethodEntry` или метода `PREVIOUS`. | Отсутствие строки **не доказывает** невозможность `PREVIOUS`: переключение назад может выполняться через индекс/список `InputMethodEntry` или обфусцированный метод с параметром направления. |

### 3.2. Ответы на 10 обязательных вопросов Исследования B

1. **Где именно обрабатывается `SWITCH_TO_NEXT_LANGUAGE`?**
   - *Частично подтверждено:* событие `Lohe;` с `Lqbc.c == -0x271b` (`-10011`) и `qae.PRESS` проходит через `Lphr;->aw(Lohe;)Z`; в DEX подтверждены enum `Lqga.SWITCH_TO_NEXT_LANGUAGE` и `LanguageSwitcherAccessPointProvider`. Точный целевой метод-потребитель за `Lphr;->aw(Lohe;)Z` — **`BLOCKED`** (требует декомпиляции APK `18.5.4`).
2. **Как определяется следующий язык?**
   - *Не доказано (`BLOCKED`):* неизвестно, использует ли обработчик `-0x271b` линейный циклический порядок списка включённых языков или MRU-порядок (последний использованный язык).
3. **Где хранится упорядоченный список включённых языков Gboard?**
   - *Не доказано (`BLOCKED`):* в `gboard-version-bindings.json` и в `GBOARD_18.5.4_BYTECODE_REFERENCE_EXTRACT.md` дескрипторы класса `InputMethodEntry` и его менеджера отсутствуют.
4. **Как определяется текущий элемент?**
   - *Не доказано (`BLOCKED`):* точный геттер текущего `InputMethodEntry` в `18.5.4` не найден в имеющихся материалах.
5. **Есть ли метод переключения на конкретный `InputMethodEntry` или индекс?**
   - *Не доказано (`BLOCKED`):* косвенным ориентиром является наличие отдельного enum-значения `Lqga.SWITCH_LANGUAGE` рядом с `Lqga.SWITCH_TO_NEXT_LANGUAGE`, но без декомпиляции обработчика `Lqga.SWITCH_LANGUAGE` точный descriptor отсутствует.
6. **Есть ли внутренний механизм отрицательного шага (`-1`)?**
   - *`NOT FOUND` в имеющихся данных / `BLOCKED` для окончательного вывода:* в 4 DEX string tables нет строки `SWITCH_TO_PREVIOUS_LANGUAGE`, а обфусцированный метод с параметром направления (`int step` / `boolean forward`) можно обнаружить только по control-flow декомпиляции.
7. **Если отдельного `PREVIOUS` нет, можно ли безопасно получить предыдущий элемент из того же списка и переключиться на него?**
   - *Архитектурно — да (целевой план `OPTION B`), но на уровне байткода `18.5.4` — `BLOCKED`*, пока из APK не извлечены три конкретных дескриптора: (а) получение упорядоченного списка включённых `InputMethodEntry`, (б) получение текущего `InputMethodEntry`, (в) вызов переключения на конкретный `InputMethodEntry`.
8. **Как исключить переключение на Samsung Keyboard, Voice Typing и другие внешние IME?**
   - *Частично подтверждено:*
     - В Gboard `18.5.4` существует `InputMethodManagerWrapper.hasOtherSwitchableImes` — нужно проверить, вызывает ли обработчик `-0x271b` переключение на другой IME при достижении конца списка языков Gboard (если да, прямой `-0x271b` опасен без перехвата/замены).
     - Переключение строго по внутреннему списку `InputMethodEntry` самого Gboard по построению **никогда** не выходит за пределы процесса/пакета `com.google.android.inputmethod.latin`.
     - Стандартный Android API `InputMethodService.switchToNextInputMethod(false)` / `switchToPreviousInputMethod()` **исключён**, так как он переключает на Samsung Keyboard / Voice Typing.
9. **Как обеспечить стабильный порядок языков, не полагаясь на MRU?**
   - Только чтением стабильного (пользовательского) списка включённых `InputMethodEntry` без мутации порядка (или с собственной детерминированной проекцией по стабильному идентификатору раскладки) и прямым переключением на `(i + 1) % N` / `(i - 1 + N) % N`. Многократная отправка `-0x271b` (`N - 1` раз) неприемлема именно из-за возможного MRU-переупорядочивания и визуального мерцания.
10. **Можно ли получить полностью двунаправленный цикл для 2, 3 и 4 языков без системного диалога?**
    - Для `N == 2` технически возможно при условии проверки, что `-0x271b` не переключает на сторонний IME и не блокируется `GboardLatinGlobeKeyIgnoreInterval` (`Lyrt;->h:F`).
    - Для **`N == 3` и `N == 4`** без системного диалога это **невозможно доказать и безопасно реализовать**, пока не найдены дескрипторы внутреннего списка `InputMethodEntry` (или внутренний метод `PREVIOUS`) в APK `18.5.4`.

---

## 4. Исследование C — конфликтующие жесты пробела и уровни конкуренции

### 4.1. Карта уровней пайплайна касаний (кто реально конкурирует за один `MotionEvent`)

```text
Уровень 0 (View-контейнер окна клавиатуры)
  KeyboardViewHolder.dispatchTouchEvent(MotionEvent)Z          [CONFIRMED: classes.dex + gboard-version-bindings.json]
    ├─ Header holder (id == keyboard_header_view_holder):
    │    GboardSwipeDismissBytecodePatch / GboardSwipeDismissRuntime  [НЕ конкурирует с пробелом пространственно,
    │                                                                  НО конкурирует за ОДИН И ТОТ ЖЕ байткод-метод!]
    └─ Body holder (не header):
         └─► Будущая стадия Stage 2 в ЕДИНОМ GboardTouchDispatchFamilyComposer (НЕ второй независимый хук!)

Уровень 1 (Поверхность клавиш)
  SoftKeyboardView.dispatchTouchEvent(MotionEvent)Z            [CONFIRMED: DEX extract]
    └─ SoftKeyboardView.A(MotionEvent, int) -> SoftKeyView (0x7f0b05b5, keyCode == 62)

Уровень 2 (Конкурирующие MotionEventHandler — ОДИН raw MotionEvent!)
  ├─ TriggerFreeCursorMotionEventHandler                       [CONFIRMED: DEX extract]
  │    ├─ FREE_CURSOR_SOURCE_SPACE_LONG_PRESS_SINGLE_KEYBOARD  [конкуренция за HOLD пробела]
  │    ├─ FREE_CURSOR_SOURCE_SPACE_LONG_PRESS_MULTI_KEYBOARD   [конкуренция за HOLD пробела]
  │    └─ FREE_CURSOR_SOURCE_SWIPE_SPACE                       [ПРЯМАЯ конкуренция за SWIPE пробела!]
  ├─ ScrubMoveMotionEventHandler (< ScrubMotionEventHandler)   [ПРЯМАЯ конкуренция за SWIPE пробела! Проверяет noScrubbing]
  ├─ ScrubDeleteMotionEventHandler (< ScrubMotionEventHandler) [НЕ конкурирует по координате (Backspace, не пробел),
  │                                                             НО потенциально рискует при отключении общего Scrub-флага!]
  ├─ Inline suggestion swipe handler                           [строка INLINE_SUGGESTION_SELECT_BY_SWIPE_ON_SPACEBAR —
  │                                                             ПРЯМАЯ конкуренция за SWIPE пробела при активной inline-подсказке!]
  └─ GestureMotionEventHandler (Glide Typing)                  [не пересекается при ACTION_DOWN вне пробела; при промахе с пробела вверх нужен soft yield]

Уровень 3 (Трекинг отдельного указателя SoftKeyView)
  Lqin;->B(SoftKeyView, F, F, J, I)V / y()V / s(J)V / r(JI)V   [CONFIRMED: GboardPointerOwnerTransformation.kt]
    ├─ 4 существующие фичи GboardSamsung (ENGLISH_QWERTY, LONG_PRESS_QUICK_ACTIONS, TOP_ROW_SWIPE, ZHUYIN_SLIDE)
    └─ Lqin;->A:Future (long-press таймер -> потенциально запускает space_long_press_ime_picker)

Уровень 4 (Пост-классификационный диспатч дискретных жестов клавиш)
  Lqil;->f(...)V -> Lqij;->o(...)V                             [CONFIRMED: GboardGestureFamilyComposer.kt]
    └─ События PRESS (keyCode == 62) и LONG_PRESS (keyCode == 62 / space_long_press_ime_picker)

Уровень 5 (Обработка IME-событий)
  Lphr;->aw(Lohe;)Z                                            [CONFIRMED: GboardLongPressQuickActionsInputEventPatch.kt]
```

### 4.2. Критические выводы по конфликтам
1. **`KeyboardViewHolder.dispatchTouchEvent(MotionEvent)Z`:**
   - В текущем `GboardSwipeDismissBytecodePatch.kt:40` стоит строгий инвариант единственности хука (`existing == 1 && instructions.first().isMethodReference(abi.reference)`).
   - **Второй независимый хук строго запрещён.** Единственный безопасный путь — рефакторинг `GboardSwipeDismissBytecodePatch` в общий `GboardTouchDispatchFamilyComposer` (Stage 1 `SWIPE_DISMISS` только для `keyboard_header_view_holder` → Stage 2 `SAMSUNG_SPACEBAR` только для body holder, с общим `ThreadLocal<Boolean>` guard синтетического `ACTION_CANCEL`).
2. **`GboardPointerOwnerTransformation` (`Lqin;`) и `GboardGestureFamilyComposer` (`Lqil;->f`):**
   - Оба лежат **ниже** Уровня 2 (`ScrubMoveMotionEventHandler`, `TriggerFreeCursorMotionEventHandler`, `INLINE_SUGGESTION_SELECT_BY_SWIPE_ON_SPACEBAR`). Поэтому основное распознавание жестов пробела **невозможно** перенести в `Lqin;` или `Lqil;`.
   - `GboardPointerOwnerTransformation` остаётся полностью нетронутым; `GboardGestureFamilyComposer` используется только как лёгкая страховочная стадия (`keyCode == 62`).
3. **Запрет преждевременного отключения общего флага `enable_scrub_move` / `0x7f14096b`:**
   - До декомпиляции `ScrubMoveMotionEventHandler`, `ScrubDeleteMotionEventHandler` и `TriggerFreeCursorMotionEventHandler` **не доказано**:
     - (а) что отключение `enable_scrub_move` / `0x7f14096b` не отключит `ScrubDeleteMotionEventHandler` (Backspace Scrub Delete);
     - (б) что отключение `enable_scrub_move` / `0x7f14096b` не сломает встроенный `FreeCursorKeyboard` (поскольку в текущем `GboardCursorTrackpadRuntime.java` эти настройки форсируются вместе с `free_cursor`).
   - Вместо отключения общего флага перехват горизонтального свайпа на Уровне 0 (`KeyboardViewHolder.dispatchTouchEvent`) с инъекцией синтетического `ACTION_CANCEL` отменяет **только** текущую сессию касания пробела и физически не затрагивает Backspace (`ScrubDeleteMotionEventHandler`).
4. **Неразрешённый конфликт с `INLINE_SUGGESTION_SELECT_BY_SWIPE_ON_SPACEBAR`:**
   - Поскольку в DEX `18.5.4` подтверждена строка `INLINE_SUGGESTION_SELECT_BY_SWIPE_ON_SPACEBAR` (и `SWIPE_ON_SPACEBAR`), при активной inline-подсказке свайп по пробелу конкурирует со сменой языка. Нужно решить на этапе трассировки APK / спецификации UX: отдавать приоритет смене языка всегда или уступать при видимой inline-подсказке.

---

## 5. Исследование D — сводная сравнительная таблица

| Возможность | Механизм | Подтверждённый descriptor | Ограничения | Статус |
|---|---|---|---|---|
| **Free Cursor long-press** | Встроенная подсистема `FreeCursorKeyboard` / `TriggerFreeCursorMotionEventHandler` / `FreeCursorMotionEventHandler` + enum `Lxfe.FREE_CURSOR_SOURCE_SPACE_LONG_PRESS_SINGLE_KEYBOARD` / `MULTI_KEYBOARD`; флаги `"free_cursor"`, `"free_cursor_lock_mode"` и настройка `"enable_scrub_move"` / `0x7f14096b` | `Loki;->g()Ljava/lang/Object;` + `Loki;->a:Ljava/lang/String;`; `Lqvt;->am(Ljava/lang/String;ZZ)Z`; `Lcht;->x(IZ)Z`; в DEX extract (без return types): `FreeCursorKeyboard.e(EditorInfo, Object)`, `eR(SoftKeyboardView, KeyboardViewDef)`, `m(Lohe)`, `h()`, `P(Lohe)` | Не прослежен control-flow `TriggerFreeCursorMotionEventHandler`, неизвестен точный метод программного запуска и зависимость от `enable_scrub_move` и `free_cursor_lock_mode` (APK отсутствует в среде) | `PARTIALLY CONFIRMED` |
| **Завершение Free Cursor** | Жизненный цикл `FreeCursorKeyboard` (`h()`, `m(Lohe)`) + `FreeCursorMotionEventHandler` + lifecycle `Lphr;` | `Lphr;->onWindowHidden()V`, `onFinishInputView(Z)V`, `onFinishInput()V`, `onDestroy()V` (`gboard-version-bindings.json`); `FreeCursorKeyboard.h()` (без return type в extract) | Не прослежено поведение `FreeCursorMotionEventHandler` на `ACTION_UP`/`ACTION_CANCEL` и гарантированное отсутствие вставки пробела без `free_cursor_lock_mode` | `BLOCKED` |
| **Отключение Swipe Space** | Блокировка `FREE_CURSOR_SOURCE_SWIPE_SPACE` и `ScrubMoveMotionEventHandler` (через точечный арбитраж в `GboardTouchDispatchFamilyComposer` или патч обработчика) | `Lcom/google/android/libraries/inputmethod/keyboard/impl/KeyboardViewHolder;->dispatchTouchEvent(Landroid/view/MotionEvent;)Z` (`gboard-version-bindings.json`); классы `ScrubMoveMotionEventHandler`, `Lxfe.FREE_CURSOR_SOURCE_SWIPE_SPACE` (DEX extract) | Неизвестно, сработает ли `ScrubMoveMotionEventHandler` / `FREE_CURSOR_SOURCE_SWIPE_SPACE` до нашего порога `D_swipe_start`, и связана ли настройка `enable_scrub_move` с Free Cursor long-press и Scrub Delete | `BLOCKED` |
| **Сохранение Scrub Delete** | Раздельный подкласс `ScrubDeleteMotionEventHandler` (< `ScrubMotionEventHandler`), привязанный к Backspace | Классы `ScrubMotionEventHandler` (`g`, `p`, `o`, `r`), `ScrubMoveMotionEventHandler`, `ScrubDeleteMotionEventHandler` (DEX extract, без полных return types) | Подтверждено разделение классов, но не доказано, читает ли `ScrubDeleteMotionEventHandler` общий ключ `"enable_scrub_move"` / `0x7f14096b` | `PARTIALLY CONFIRMED` |
| **NEXT language** | Событие `Lohe;` с `Lqbc.c == -0x271b` (`-10011`), payload `"globe"`, `qae.PRESS`, enum `Lqga.SWITCH_TO_NEXT_LANGUAGE` | `Lphr;->aw(Lohe;)Z`, `Lohe;->a:Lqae;`, `b:[Lqbc;`, `Lqbc;->c:I`, `e:Ljava/lang/Object;` (`gboard-version-bindings.json` + `GboardLongPressQuickActionsReflectionHandles.java`); `Lqga.SWITCH_TO_NEXT_LANGUAGE` (DEX extract) | Не прослежен downstream-обработчик `-0x271b`: не доказано, циклический он или MRU, и может ли переключить на внешний IME (`hasOtherSwitchableImes`) | `PARTIALLY CONFIRMED` |
| **PREVIOUS language** | Поиск буквального идентификатора или обратного keycode / метода шага `-1` | Не найден (строка `SWITCH_TO_PREVIOUS_LANGUAGE` отсутствует во всех 4 DEX; в репо нет обратного keycode) | Отсутствие строки не доказывает невозможность `PREVIOUS`; нужен поиск обфусцированного метода / индексного декремента в JADX/Baksmali | `NOT FOUND` |
| **Получение списка языков** | Внутренний менеджер `InputMethodEntry` Gboard (упорядоченный список включённых раскладок) | Не подтверждён (отсутствует в `gboard-version-bindings.json`, исходниках репо и текущем DEX extract) | Невозможно извлечь дескриптор без декомпиляции `Gboard-v18.5.4.993608717-release-arm64-v8a.apk` | `BLOCKED` |
| **Получение текущего языка** | Геттер активного `InputMethodEntry` во внутреннем менеджере Gboard | Не подтверждён (отсутствует в `gboard-version-bindings.json`, исходниках репо и текущем DEX extract) | Невозможно извлечь дескриптор без декомпиляции целевого APK `18.5.4` | `BLOCKED` |
| **Переключение на конкретный язык** | Внутренний метод переключения на целевой `InputMethodEntry` / `Lqga.SWITCH_LANGUAGE` | В DEX extract подтверждён только enum `Lqga.SWITCH_LANGUAGE` (без дескриптора целевого метода) | Невозможно извлечь сигнатуру и проверить защиту от выхода из Gboard без декомпиляции APK `18.5.4` | `BLOCKED` |

---

## 6. Финальный архитектурный вердикт Этапа 4.1

### 6.1. Cursor: **`BLOCKED`**
- **Почему `BLOCKED`, а не `OPTION A` или `OPTION B` прямо сейчас:**
  - Доказано существование встроенного `FreeCursorKeyboard`, `FreeCursorMotionEventHandler`, `TriggerFreeCursorMotionEventHandler`, констант `Lxfe.FREE_CURSOR_SOURCE_SPACE_LONG_PRESS_SINGLE_KEYBOARD / MULTI_KEYBOARD / SWIPE_SPACE / DIRECT_KEYCODE`, а также то, что upstream-патч `GboardCursorTrackpad` уже активирует режим трекпада на удержании пробела через `"free_cursor"`, `"free_cursor_lock_mode"` и `"enable_scrub_move"` / `0x7f14096b`.
  - Однако в контейнере отсутствует APK `18.5.4`, поэтому **не доказаны**: (1) точный descriptor и условия запуска `TriggerFreeCursorMotionEventHandler` / `FreeCursorKeyboard`, (2) возможность работать без `free_cursor_lock_mode` с чистым выходом по `ACTION_UP`, (3) возможность отключить `FREE_CURSOR_SOURCE_SWIPE_SPACE` и `ScrubMoveMotionEventHandler` без поломки Free Cursor long-press и без поломки `ScrubDeleteMotionEventHandler`.
  - **Приоритет после разблокировки:** первым проверяется **`OPTION A`** (переиспользование `FreeCursorKeyboard`); если трассировка докажет жёсткую связку `SWIPE_SPACE` или несовместимость UX с удержанием без lock mode — немедленный переход на **`OPTION B`** (`GboardSpacebarCursorController` через `finishComposingText()` + сбалансированные `InputConnection.sendKeyEvent(KEYCODE_DPAD_LEFT/RIGHT)`).

### 6.2. Language switching: **`BLOCKED`**
- **Почему `BLOCKED`:**
  - Подтверждён только путь `NEXT` (`-0x271b` через `Lphr;->aw(Lohe;)Z`, `Lqga.SWITCH_TO_NEXT_LANGUAGE`).
  - Буквальный `SWITCH_TO_PREVIOUS_LANGUAGE` в 4 DEX не найден, а дескрипторы внутреннего списка `InputMethodEntry`, текущего элемента и метода переключения на конкретный `InputMethodEntry` (или `Lqga.SWITCH_LANGUAGE`) отсутствуют и в репозитории, и в текущем поверхностном DEX extract.
  - Без этих 3 дескрипторов из APK `18.5.4` невозможно безопасно гарантировать детерминированный двунаправленный цикл для `3` и `4` языков без MRU, без открытия системного IME picker и без выхода в Samsung Keyboard.
  - **Приоритет после разблокировки:** трассировка обработчика `Lqga.SWITCH_TO_NEXT_LANGUAGE` (`-0x271b`) и `LanguageSwitcherAccessPointProvider` в JADX/Baksmali сразу переведёт вердикт либо в **`OPTION A`** (если найдётся скрытый шаг `-1`), либо в **`OPTION B`** (собственный детерминированный цикл `(i ± 1 + N) % N` поверх внутреннего списка `InputMethodEntry` Gboard).

---

## 7. Минимальный целевой пакет декомпиляции для закрытия всех блокеров

Поскольку APK `Gboard-v18.5.4.993608717-release-arm64-v8a.apk` находится на локальной машине владельца проекта (и не должен коммититься в GitHub), достаточно выполнить локально **ровно один** проход JADX/Baksmali и сохранить компактную текстовую выжимку (только дескрипторы, call-графы и ключевые ветвления — без дампа исходников APK) в `docs/GBOARD_18.5.4_STAGE_4_2_CONTROL_FLOW_EXTRACT.md`:

1. **Блок Free Cursor + Scrub (закрывает вердикт Cursor):**
   - Декомпилировать 6 классов:
     - `com.google.android.libraries.inputmethod.freecursor.FreeCursorKeyboard`
     - `com.google.android.libraries.inputmethod.freecursor.FreeCursorMotionEventHandler`
     - `com.google.android.libraries.inputmethod.freecursor.TriggerFreeCursorMotionEventHandler`
     - `com.google.android.libraries.inputmethod.motioneventhandler.scrubmove.ScrubMotionEventHandler`
     - `com.google.android.libraries.inputmethod.motioneventhandler.scrubmove.ScrubMoveMotionEventHandler`
     - `com.google.android.libraries.inputmethod.motioneventhandler.scrubmove.ScrubDeleteMotionEventHandler`
   - Найти все ссылки (`xrefs`) на `Lxfe.FREE_CURSOR_SOURCE_SPACE_LONG_PRESS_SINGLE_KEYBOARD`, `FREE_CURSOR_SOURCE_SPACE_LONG_PRESS_MULTI_KEYBOARD`, `FREE_CURSOR_SOURCE_SWIPE_SPACE`, `FREE_CURSOR_SOURCE_DIRECT_KEYCODE`, `"enable_scrub_move"`, `0x7f14096b` и `"space_long_press_ime_picker"` + тело `Lqin;->y()V`.
2. **Блок двунаправленного переключения языков (закрывает вердикт Language Switching):**
   - Найти все ссылки (`xrefs`) на `Lqga.SWITCH_TO_NEXT_LANGUAGE`, `Lqga.SWITCH_LANGUAGE`, целочисленную константу `-10011` (`-0x271b`), `"Input.switchToNextLanguage.%s"` и декомпилировать `LanguageSwitcherAccessPointProvider` + найденный обработчик переключения `InputMethodEntry`.
   - Зафиксировать точные дескрипторы: список включённых `InputMethodEntry`, текущий `InputMethodEntry`, метод переключения на конкретный `InputMethodEntry` (и проверить, затрагивает ли он внешние IME).
