# Переход на Kotlin Multiplatform — шаг 1: разбор кода

Ветка `kmp`. `main` не меняется, пока шаг 1 не пройдёт тесты и скриншоты.

## Поправка после чтения кода (шаг 1)

Первый разбор по импортам был слишком оптимистичным. `Model.kt` и экраны не содержат `import android.*`,
но завязаны на Android иначе:

- строки интерфейса: `R.string.*`, `str(...)`, `@StringRes`, `Lang` (на `Context` и `Resources` Android);
- даты: `java.time` (`LocalDate`, `LocalDateTime`) — на iOS его нет, нужен `kotlinx-datetime`;
- `String.format` — только JVM.

Поэтому `Model.kt` целиком в общий модуль сразу не переезжает. Сделано: чистая часть (`matchRepeat`, `minutesToTime`,
`ContactRef`, `PhoneNumber`, `OutcomeKind`, `ContactStat`, `DeleteScope`, напоминания на весь день) вынесена в
`shared/.../model/Rules.kt` с теми же именами пакета — вызовы в приложении не менялись.
Затем (общий слой строк): общий код берёт тексты по ключу через `L10n`/`StringProvider` (`shared/.../model/L10n.kt`);
на Android ключ — это имя строки в `strings.xml`, мост — `AndroidStrings` в `Lang.kt`, язык по-прежнему выбирается через `Lang`.
В `shared/.../model/Types.kt` переехали `EventType`, `Outcome`, `MailKind`/`MailInfo`, `CalendarInfo`, подписи повторов и напоминаний.
Значки и цвета типов остались в приложении: `EventType.icon` и `EventType.color` — расширения в `app/.../model/Model.kt`
(их нужно импортировать: `import ru.palmdate.app.model.icon` / `.color`). Новые строки для общего кода добавлять
в `strings.xml` как обычно и использовать по имени.
Даты: слой `shared/.../time/Time.kt` на `kotlinx-datetime` с теми же именами методов, что в `java.time`
(`plusDays`, `atStartOfDay`, `LocalDate.now()`, `format(HM)`…): в файлах с датами нужен `import ru.palmdate.app.time.*`.
Экраны, `DayViewModel`, `PalmEvent`, тесты — уже на `kotlinx.datetime`. Репозиторий календаря (Android) внутри считает
зоны и миллисекунды на `java.time`, перевод на границе — `data/TimeBridge.kt`. Названия месяцев и дней недели —
пока через `java.time` (`ui/DateNames.kt`), для iOS потребуется своя реализация.
`PalmEvent` и `NewEvent` тоже в `shared` (`model/Events.kt`). В приложении (`Model.kt`) остались только `EventType.icon` и `.color`.
Важно: у полей классов из `shared` Kotlin не делает «умное приведение» типа в приложении — писать `x?.y` или `x!!` после проверки.
Остальное — по очереди: сначала строки (общий слой вместо `R.string`), потом даты, потом `EventType`/`PalmEvent`/`Outcome`.

## Что уже без Android-зависимостей по импортам (кандидаты, после замены строк и дат)

- `model/Model.kt` (остаток: `EventType`, `PalmEvent`, `Outcome`, `MailInfo`, `CalendarInfo`, `NewEvent`)
- `ui/Views.kt` (повестка, неделя, месяц, год)
- `ui/DayScreen.kt` — кроме связей с ViewModel и поворота, см. ниже
- `ui/NewEventSheet.kt`
- `ui/StatsSheet.kt`
- `ui/ContactHistory.kt`
- `ui/PalmSheet.kt`, `ui/PalmWidgets.kt`
- `ui/theme/PalmTheme.kt`

## Android-зависимые файлы (остаются в `app` или получают платформенный интерфейс)

Календарь и контакты (CalendarContract, ContactsContract):
- `data/CalendarRepository.kt` (901 строка — самый большой платформенный кусок, идёт на EventKit)
- `data/ContactsRepository.kt`

Хранилища и файлы (Context, Uri, FileProvider, Room):
- `data/LinkDb.kt` (Room, в KMP-версии Room поддерживается — проверим)
- `data/Settings.kt`, `data/OwnAddresses.kt`, `data/Backup.kt`, `data/TripFiles.kt`
- `data/MailApps.kt`, `data/CallOptions.kt`

Интерфейс и системные действия:
- `ui/Actions.kt`, `ui/Caller.kt`, `ui/MailUi.kt`, `ui/SettingsSheet.kt`, `ui/Hinge.kt`
- `Lang.kt` (строки и Locale), `MainActivity.kt`, `BirthdayReminder.kt`, `DayViewModel.kt`

## Что делаем дальше

1. Создать модуль `shared` с Kotlin Multiplatform, перенести `model/Model.kt` и тесты логики (`LogicTest`, `RotationTest`). Android-модуль подключает `shared`, поведение не меняется.
2. Описать платформенные интерфейсы в `shared` (календарь, контакты, хранилище, файлы, почта, мессенджеры, напоминания, настройки языка).
3. Перенести UI-файлы из списка выше в `shared`, после каждого шага сверять скриншоты с эталоном (`screenshots`).
4. Проверки: `tests.yml` запускается вручную через `workflow_dispatch` на любой ветке.

Эта таблица — первичный разбор. Точный список (по зависимостям, а не по импортам) уточним на шаге 1.

## Экраны на Compose Multiplatform (идёт по частям)

Сделано: в `shared` подключён Compose Multiplatform (плагин `org.jetbrains.compose` 1.7.1, runtime/foundation/material3/ui);
туда переехали `ui/theme/PalmTheme.kt` (тема и цвета `Palm`) и `ui/PalmWidgets.kt` (`dottedRule`, `PalmButton`).
`keepAboveKeyboard` (клавиатура Android) остался в приложении, файл `ui/KeyboardUtils.kt`.

**Ловушка при переносе:** каждый Kotlin-файл верхнего уровня превращается в класс с именем файла (`PalmWidgetsKt`).
Если из файла часть кода переехала в `shared`, а часть осталась в приложении, у файлов должны быть РАЗНЫЕ имена,
иначе компиляция проходит, а при запуске — `NoSuchMethodError` (так упали 90 тестов, пока файл не переименовали).
Проверка: `comm -12 <(cd app/src/main/java && find . -name '*.kt'|sort) <(cd shared/src/commonMain/kotlin && find . -name '*.kt'|sort)`
не должна ничего выводить.

Ещё переехало (проход 6, build+тесты зелёные): `ui/Views.kt` (повестка/неделя/месяц/год), `ui/EventRows.kt` (`EventLine`, `TimeLabel`,
`OutcomeBadge`), `ui/DateTitles.kt` (подписи дат), `ui/DayNames.kt` (`expect/actual` названий дней и месяцев; actual для Android готов,
**для iOS — ещё нет**), `data/AppSettings.kt` (настройки без JSON; JSON и хранилище остались в приложении), `WeekMath.kt`, значки типов
(`model/EventStyle.kt`), `str("ключ")`/`plu("ключ", n)` в общем коде.

Дальше: DayScreen (шапка, день, панель кнопок), затем карточка и «Новое», история, статистика, настройки.
Android-вещи (Context, Uri, диалоги выбора файлов, поворот и складной экран) прячем за интерфейсами.
