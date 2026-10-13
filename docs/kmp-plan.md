# Переход на Kotlin Multiplatform — шаг 1: разбор кода

Ветка `kmp`. `main` не меняется, пока шаг 1 не пройдёт тесты и скриншоты.

## Что уже без Android-зависимостей (кандидаты в общий модуль `shared`)

Нет ни одного импорта `android.*`, только `androidx.compose` (он переносится через Compose Multiplatform):

- `model/Model.kt`
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
