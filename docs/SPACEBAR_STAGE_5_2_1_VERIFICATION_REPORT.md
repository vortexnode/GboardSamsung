# Stage 5.2.1 — Финальная проверка реестра и контрактов Samsung Spacebar Gestures

**Репозиторий:** `vortexnode/GboardSamsung`  
**Ветка:** `main`  
**Проверяемый базовый коммит:** `e7a40e5357ec0dc16aef65f95477ef56499a4c18`  
**Целевой Gboard:** `18.5.4.993608717-release-arm64-v8a`  
**Режим:** read-only аудит опубликованного исходного кода; исходники патчей не изменялись.

## 1. Итог

Stage 5.2.1 завершён на уровне статического аудита исходников и контрактов.

- **Не обнаружена функциональная зависимость** Samsung Spacebar Gestures от SwipeDismiss.
- Общий touch composer реализует четыре ожидаемых сценария на уровне исходного кода.
- Ресурсная очистка зарегистрирована как зависимость только публичного патча Samsung Spacebar Gestures; Globe и перечисленные независимые функции не входят в условия удаления.
- `swipe_dismiss_header_dispatch` в `required_bindings` — требование к доказательству наличия общего метода `KeyboardViewHolder.dispatchTouchEvent(MotionEvent)Z` в целевом Gboard, а не зависимость от функциональности SwipeDismiss.
- Полный Gradle suite и перечисленные registry/runtime ABI tests **не были запущены** в доступной среде. Поэтому проверка не объявляется полной компиляционной/интеграционной верификацией.
- Исправления исходного кода не потребовались. Коммит с изменениями исходников не создавался.

## 2. Текущее состояние Git

Сравнение `04f89b3bb6a80bc5f6d5e5c403220853cd21eb56...e7a40e5357ec0dc16aef65f95477ef56499a4c18` подтверждает один коммит Stage 5.2 и 18 затронутых файлов (с учётом перемещённых файлов). Последний опубликованный коммит на момент аудита — `e7a40e5`.

Изменения Stage 5.2 ограничены реализацией жестов пробела, общим обработчиком касаний, переключателем языков, реестром/каталогом и соответствующими тестами/отчётом. В сравнении не обнаружены посторонние продуктовые изменения или бинарные APK/DEX-файлы.

## 3. Независимость публичных патчей

### Samsung Spacebar Gestures

**PASS — статическая проверка.**

В `GboardPatchRegistry.kt` публичный патч `gboardSamsungSpacebarGesturesPatch`:
- имеет `featureId = "samsung_spacebar_gestures"`;
- зависит от `gboardSpacebarGestureBytecodePatch` и `gboardSpacebarGestureResourcePatch`;
- не зависит от `gboardSwipeDismissPatch`, `gboardSwipeDismissBytecodePatch` или `gboardSwipeDismissFeatureMarkerPatch`.

### SwipeDismiss

**PASS — статическая проверка.**

Публичный `gboardSwipeDismissPatch` зависит от settings patch, своего feature marker и своего bytecode patch. Зависимости на Spacebar Gestures и его resource patch отсутствуют.

### Владение resource patch

**PASS — статическая проверка регистрации.**

В просмотренном реестре `gboardSpacebarGestureResourcePatch` включён в зависимости только `gboardSamsungSpacebarGesturesPatch`. В репозитории имеется отдельный тест `spacebarResourcePatchIsOwnedByOnlyOnePublicPatchAcrossCatalog`, но сам JUnit-тест в этой среде не исполнялся.

## 4. Shared Touch Composer — четыре сценария

Исходник `GboardKeyboardViewHolderTouchComposer.kt` проверен непосредственно.

| Выбор пользователя | Поведение, заданное исходным кодом | Статус |
|---|---|---|
| Только Samsung Spacebar Gestures | Один вызов `GboardSpacebarGestureRuntime.onHolderTouch(p0, p1)` | PASS — статический анализ |
| Только SwipeDismiss | Один вызов `GboardSwipeDismissRuntime.onHeaderTouch(p0, p1)` | PASS — статический анализ |
| Оба патча | Один общий пролог: сначала SwipeDismiss; если он не поглотил событие, вызывается Spacebar Gestures | PASS — статический анализ |
| Ни один патч | Пустой набор выборок; `finalize` немедленно выходит без изменения `dispatchTouchEvent` | PASS — статический анализ |

Дополнительные наблюдения:
- Выборки хранятся раздельно по `BytecodePatchContext` в синхронизированном `WeakHashMap`.
- Повторное добавление одного feature идемпотентно благодаря `MutableSet`.
- Вставка общего пролога выполняется один раз в `KeyboardViewHolder.dispatchTouchEvent(MotionEvent)Z`.
- При совместном включении SwipeDismiss выполняется первым; Spacebar получает событие только если SwipeDismiss вернул `false`.
- Проверка существующих method references предназначена для обнаружения повторной/неправильной вставки и не допускает незаметного дублирования.

**Ограничение:** четыре сценария подтверждены чтением ветвления и selection store, но фактический Morphe patch execution graph и итоговый DEX не были созданы. Тест selection store не заменяет интеграционный тест с настоящим patch engine.

## 5. Разбор `required_bindings: ["swipe_dismiss_header_dispatch"]`

**Вывод: не является скрытой функциональной зависимостью от SwipeDismiss. Это общий target binding с неудачно специфичным историческим именем.**

Проверены:
- contribution `samsung_spacebar_gestures.dispatch` в `gboard-port-product-catalog.json`;
- binding `swipe_dismiss_header_dispatch` в `gboard-version-bindings.json`;
- `TargetBindingCompiler` и `GboardTargetBuildCompiler`.

Binding разрешается в конкретную сигнатуру:
`KeyboardViewHolder.dispatchTouchEvent(MotionEvent): boolean`.

Компилятор binding requirements собирает `required_bindings` из admitted contributions, а admission compiler проверяет наличие соответствующего ключа в `target_contract.binding_exports` PortBundle. Это проверка наличия/доказательства целевого метода для текущего Gboard. Она сама по себе:
- не включает публичный SwipeDismiss patch;
- не вызывает `GboardSwipeDismissRuntime`;
- не добавляет зависимость `depends_on_feature_ids`;
- не требует выбора SwipeDismiss пользователем.

В коде Spacebar используется общий composer, который внедряет собственный runtime call в тот же метод. Таким образом, совпадает точка перехвата, а не функциональность двух патчей.

**Решение:** оставить binding без переименования на этом этапе. Нейтрального эквивалентного binding ID в текущем каталоге нет. Переименование потребовало бы синхронно обновлять каталог, профиль биндингов, PortBundle/binding evidence, digest и contract tests; это лишний риск без исправления реальной функциональной зависимости.

## 6. Ресурсная очистка и сохранение функций

**PASS — статический анализ условий удаления.**

`GboardSpacebarGestureResourcePatch.kt` удаляет только:
- `LONG_PRESS` + `SWITCH_KEYBOARD` / `-10004` с `data="free_cursor"`;
- `LONG_PRESS` + `SHOW_SYSTEM_IME_PICKER` / `-10022` с `data="space"`;
- регистрации `ScrubMoveMotionEventHandler`;
- регистрации `TriggerFreeCursorMotionEventHandler`.

Не затрагиваются условиями удаления:
- обычный `PRESS` пробела (`SPACE`);
- `PRESS` Globe (`SWITCH_TO_NEXT_LANGUAGE`, `data="globe"`);
- `LONG_PRESS` Globe (`SHOW_SYSTEM_IME_PICKER`, `data="globe"`);
- `ScrubDeleteMotionEventHandler`;
- `InlineSuggestionScrubSpaceMotionEventHandler`.

В тесте resource patch присутствуют явные проверки этих сохранённых элементов. JUnit-тесты resource patch в рамках данного аудита повторно не запускались.

## 7. Registry, admission и ABI — статус проверок

| Проверка | Результат в этой среде |
|---|---|
| Статическая проверка зависимостей публичных патчей | **PASS** |
| Статическая проверка единственного владельца resource patch | **PASS** |
| Статическая проверка ветвей shared touch composer | **PASS** |
| Статическая проверка общего binding и его роли в admission compiler | **PASS** |
| Статическая проверка Globe / SPACE / ScrubDelete / InlineSuggestionScrubSpace | **PASS** |
| `GboardSamsungSpacebarGesturesPatchRegistryTest` | **NOT RUN** — нужен Gradle/Morphe patch runtime |
| `GboardSwipeDismissPatchRegistryTest` | **NOT RUN** — нужен Gradle/Morphe patch runtime |
| `GboardPublishedPatchCatalogContractTest` | **NOT RUN** — нужен Gradle |
| `GboardPortProductCatalogContractTest` | **NOT RUN** — нужен Gradle |
| Генерация и проверка `GboardTargetAdmission` | **NOT RUN** — выполняется Gradle task `generateGboardVersionBindings` |
| `GboardCapabilityWiringVerifier` | **NOT RUN** — требует скомпилированные patch classes |
| `CompiledPatchRuntimeCallInventoryTest` | **NOT RUN** — требует скомпилированные patch classes и runtime artifacts |
| `RuntimeAbiVerifierTest` | **NOT RUN** — требует Gradle test runtime |
| Полный `:patches:test` | **NOT RUN** |
| Полный `:extensions:extension:testDebugUnitTest` | **NOT RUN** |
| `git diff --check` | **NOT RUN** — в доступной среде нет локального Git checkout; проверка GitHub compare не эквивалентна этой команде |
| Сборка `.mpp` | **NOT RUN** — намеренно |
| Сборка APK | **NOT RUN** — намеренно |

Причина невозможности выполнить Gradle здесь: доступный GitHub connector предоставляет чтение исходников/метаданных и запись файлов, но не предоставляет исполняемую рабочую копию проекта или Gradle runner. В Stage 5.2 implementation report также зафиксировано отсутствие разрешённой зависимости `app.morphe:morphe-patcher:2.1.0` в тогдашнем build-контейнере. Никакие stub-компиляции не засчитываются здесь как Gradle integration tests.

В просмотренном репозитории нет отдельного GitHub Actions workflow, запускающего Gradle test suite для этого коммита; доступные workflow-файлы относятся к upstream import и release/preview задачам.

## 8. Изменения и остаточные риски

**Изменения исходного кода Stage 5.2.1:** отсутствуют.  
**Исправления:** не потребовались.  
**Новый runtime механизм:** не добавлялся.  
**Globe toggle:** не добавлялся.  
**SwipeDismiss:** не удалялся и не переписывался.

Остаются обязательные проверки перед сборкой/установкой:
1. Прогнать перечисленные Gradle registry, contract, capability wiring и runtime ABI tests в полноценной среде с Morphe Patcher 2.1.0.
2. Проверить реальный generated `GboardTargetAdmission` и фактический patch dependency graph.
3. После успешного Gradle suite выполнить отдельную проверку на Samsung Galaxy S22 SM-S901U1 / Android 16 / One UI 8 с Gboard 18.5.4: только Spacebar, только SwipeDismiss, оба вместе; затем проверить 1–4 языка и сохранность Globe/обычного ввода.

## 9. Финальное решение

**Статический аудит Stage 5.2.1: PASS.**  
**Полная интеграционная/Gradle верификация: NOT RUN.**  
**Готовность к сборке и установке: пока НЕ подтверждена.**

Текущую реализацию не откатывать и не менять наугад. Следующий безопасный шаг — выполнить полный Gradle test suite в среде, где доступен Morphe Patcher 2.1.0. До этого не собирать `.mpp`/APK и не считать работу проверенной на устройстве.
