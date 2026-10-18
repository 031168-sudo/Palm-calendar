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

Проход 7 (зелёный): главный экран без данных и окон — `ui/MainFrame.kt` (шапка, день, панель кнопок, раскладки планшет/книжка/«ноутбук»),
`CalState.kt` (`ViewMode`, `CalState`), `ui/Hinge.kt` (сгиб без Android-типов), `ui/WindowSize.kt` (`expect/actual`, для iOS actual ещё нет).
В `DayScreen.kt` остались окна и связка с `DayViewModel`.

Проход 8 (зелёный): `ui/NewEventSheet.kt` («Новое» + карточка события), `ui/PalmSheet.kt`, `ui/EmailList.kt`, `ui/ActionLabels.kt`.
Всё Android-ское спрятано за `ui/Platform.kt`: `PlatformActions` (карты, открыть документ, «свой адрес», запомненный способ связи,
окно «Настроить повтор»; на Android — `AndroidPlatform`, подключается в `MainActivity` через `LocalPlatform`), `expect`:
`PlatformBackHandler`, `keepAboveKeyboard`, `rememberDocumentPicker`, `longDate`, `windowWidthDp`, `dayOfWeekName/monthName`.
**Для iOS нужны `actual` ко всем этим `expect` и своя `PlatformActions`.** `RepeatSpec`/`RepeatDialog` пока остаются в приложении (java.time).

Проход 9 (зелёный): `ContactHistory`, `StatsSheet`, `CalendarsSheet`, `LocalCaller` — в `shared`.
В приложении остались: `DayScreen.kt` (склейка окон), `SettingsSheet` (обновления, разрешения, резервная копия, смена языка — очень «андроидные»),
`MailUi`, `Caller`, `RepeatDialog`/`Repeat.kt`, `DayViewModel` и всё `data/`.

Проход 10 (зелёный): `SettingsSheet` — в `shared`; андроидное (язык с пересозданием окна, разрешение на уведомления, почтовая программа,
резервная копия, проверка обновлений) — за интерфейсом `SettingsPlatform` (`ui/SettingsPlatform.kt`, на Android — `AndroidSettings.kt`).
Запись настроек из общего кода — `CurrentSettings.update`.

Проход 11 (зелёный): `DayModel` (состояние главного экрана и все действия) — в `shared`. Граница с системой — интерфейс `AppBackend`
(`AppBackend.kt`: календарь, контакты, документы, маленькое хранилище, слежение за изменениями снаружи). На Android — `data/AndroidBackend.kt`
(оборачивает `CalendarRepository`, `ContactsRepository`, Room); `DayViewModel` стала тонкой обёрткой (`vm.model` — общая модель, плюс
почта/резервная копия/обновления, которые только для Android).

Осталось в приложении: `DayScreen.kt` (склейка окон), `MailUi`, `Caller`, `RepeatDialog`/`Repeat.kt`, `data/*` (Android-реализация).

Проход 12 (зелёный): `.github/workflows/ios.yml` — компиляция `shared` под iOS на macOS-раннере (`:shared:compileKotlinIosSimulatorArm64`).
Общий код собирается под iOS; потребовалось только: `ioDispatcher` (expect/actual вместо `Dispatchers.IO`) и `actual` для iOS в
`shared/src/iosMain/.../ui/Platform.ios.kt` (названия дат через NSDateFormatter, ширина окна, заглушки «Назад»/клавиатуры/выбора файла).
Выбор файла на iOS — пока заглушка (UIDocumentPicker).

Дальше (iOS):
1. Оболочка: iOS-фреймворк из `shared` (`ComposeUIViewController`), Xcode-проект, `AppBackend`/`PlatformActions`/`SettingsPlatform` для iOS
   (на первом шаге — с тестовыми данными, чтобы увидеть экраны).
2. Сборка приложения на macOS в GitHub Actions (`xcodebuild`, сначала без подписи, на симулятор).
3. EventKit (календарь), Contacts, почта/мессенджеры; подпись и TestFlight (понадобятся данные аккаунта Apple).

## iPhone-приложение (сделано, раздаётся через TestFlight)

**Состав.** `shared/src/iosMain/...`: `MainViewController.kt` (вход для SwiftUI; `ComposeUIViewController`), `EventKitBackend.kt`
(`AppBackend` на EventKit + Contacts), `IosStore.kt` (NSUserDefaults: настройки, свои адреса, запомненный способ связи, почтовая программа),
`IosStrings.kt` (+ `GeneratedStrings.kt`: Gradle-задача `genIosStrings` превращает `strings.xml` ru/en в код; в общем коде — `str("ключ", …)`),
`ui/IosPlatform.kt` (`PlatformActions`, `SettingsPlatform`), `ui/IosMail.kt` (почта), `ui/IosCaller.kt` («Связаться»), `ui/Platform.ios.kt`.
Xcode-проект генерирует `xcodegen` из `iosApp/project.yml` (там же `MARKETING_VERSION`, `LSApplicationQueriesSchemes`, тексты разрешений).

**Метки событий.** Как на Android: тип/контакт/номер/письмо — в заметках события EventKit `[palm:t=TYPE;c=lookupKey;p=phone;m=mail]`
(значения в процентной кодировке) + строка «Итог: …». Метка письма: `N|адрес` (написать) или `R|адрес|имя` (ответить; тема — из заголовка после «: »).

**Почта.** Только через почтовые программы (Mail, Gmail, Outlook, Яндекс Почта; схемы в Info.plist). «Написать» открывает новое письмо;
«Ответить» сначала показывает, что будет скопировано для поиска, и только по кнопке копирует и запускает программу; по возвращении —
«Письмо отправлено?» → итог. Выбор программы с галочкой «всегда», сброс — Настройки → Почта.

**Связаться.** Телефон (`tel:`) + установленные WhatsApp, Telegram, Viber (чат по номеру); есть ли человек в мессенджере iPhone не показывает
(Android видит отметки в контактах). MAX — нет ссылки по номеру, пока не добавлен. Галочка «Всегда так для этого человека» хранится в `IosStore`.

**Карты.** `ui/IosMaps.kt`: Apple Карты, Яндекс Карты, Яндекс Навигатор, 2ГИС, Google Карты (какие установлены — по `canOpenURL`, схемы в Info.plist); одна программа — открываем сразу, несколько — окно выбора с галочкой «всегда», сброс в Настройки → Карты.

**Резервная копия.** `ui/IosBackup.kt`: JSON `{"format":"datebook-ios-1","data":{…}}` со всеми ключами `pd_*` из NSUserDefaults (кроме `pd_last_error`). Сохранение — `UIActivityViewController` (Файлы/Поделиться), восстановление — `UIDocumentPickerViewController`. События и их метки лежат в календаре, поэтому в копию не входят. Файлы с Android (`datebook-1`) на iPhone не читаются и наоборот.

**Документы.** `ui/IosFiles.kt`: выбор — `UIDocumentPickerViewController` (asCopy), копия в `Documents/trips/<событие>/`, список в `IosStore` (`files_<событие>`), просмотр — `UIDocumentInteractionController` (если формат не показывается — «Поделиться»). Файлы в резервную копию iPhone не входят.

**Правила Kotlin/Native, о которые уже споткнулись.** Вложенные enum — `EKEntityType.EKEntityTypeEvent`; `NSError**` передаём `null`;
исключения Objective-C не ловятся `runCatching` (поэтому места вроде списка календарей и контактов защищены заранее);
`isImeVisible` в iosMain нет; `OnFocusBehavior` — `androidx.compose.ui.uikit.OnFocusBehavior`; для iOS-сборки нужно `-Xmx3g` (gradle.properties).

**TestFlight.** `.github/workflows/ios-testflight.yml` (macos-15, Xcode 26): релизный фреймворк → xcodegen → архив с автоподписью по ключу App Store Connect →
экспорт → загрузка через REST API (Xcode по ключу не логинится — JWT-ошибка). Запуск — изменить число в `.github/testflight-trigger.txt` и запушить в `kmp`.
Версия — `MARKETING_VERSION` из `iosApp/project.yml`, номер сборки — номер прогона. Секреты (только в GitHub): `ASC_KEY_ID`, `ASC_ISSUER_ID`,
`ASC_KEY_P8`, `ASC_TEAM_ID`. Шаг «Clean temporary certificates» удаляет только временные сертификаты «Created via API» (иначе упираемся в лимит);
настоящий «Apple Distribution: Alfanomia OOO» не трогать. `asc-status.yml` (+ `asc-status-trigger.txt`) показывает состояние сборок и тестировщиков.
Ключи и .p8 в чат не присылать.

**Правила Apple, которые выяснили.** Первой внешней группе нужна проверка Beta App Review; одновременно на проверке — только одна сборка с данной
версией (новая версия 0.2 → параллельная проверка); сборка живёт 90 дней; во внутреннюю группу попадают только участники команды;
письмо «Build completed processing» — не одобрение.

**Ещё не сделано на iPhone.** Окно «Настроить…» для своих повторений (заглушка), «Открыть в системном календаре», жест «назад», уведомления о днях рождения, настройки кроме языка и почты, MAX.
