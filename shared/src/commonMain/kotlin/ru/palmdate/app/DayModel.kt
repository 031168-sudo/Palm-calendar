package ru.palmdate.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import ru.palmdate.app.data.CurrentSettings
import ru.palmdate.app.data.StartView
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.ContactStat
import ru.palmdate.app.model.DeleteScope
import ru.palmdate.app.model.EmailContact
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.NewEvent
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.model.PhoneNumber
import ru.palmdate.app.time.*
import ru.palmdate.app.ui.TripDoc

/**
 * Состояние главного экрана и все действия (навигация, создание, правка, удаление).
 * Не знает про Android: всё «железное» берёт у [backend]; [scope] — главный поток экрана.
 */
class DayModel(private val scope: CoroutineScope, private val backend: AppBackend) {
    private val _state = MutableStateFlow(
        CalState(
            mode = when (CurrentSettings.current.startView) {
                StartView.DAY -> ViewMode.DAY
                StartView.AGENDA -> ViewMode.AGENDA
                StartView.LAST ->
                    runCatching { ViewMode.valueOf(backend.getString(KEY_MODE)!!) }.getOrDefault(ViewMode.DAY)
            },
        ),
    )
    val state = _state.asStateFlow()

    private var started = false
    private var loadJob: Job? = null

    /** Вызывается после выдачи разрешений. */
    fun start() {
        if (started) return
        started = true
        backend.hiddenCalendars = _hidden.value
        backend.invalidateContacts()
        backend.observeExternalChanges { reload() }
        reload()
    }

    /* ---- Навигация ---- */

    fun select(date: LocalDate) = go(_state.value.mode, date)

    fun setMode(mode: ViewMode, date: LocalDate = _state.value.date) {
        backend.putString(KEY_MODE, mode.name)
        go(mode, date)
    }

    /** Свайп: следующий/предыдущий период текущего вида. */
    fun shiftPeriod(dir: Int) {
        val s = _state.value
        val d = s.date
        select(
            when (s.mode) {
                ViewMode.DAY -> d.plusDays(dir.toLong())
                ViewMode.AGENDA, ViewMode.WEEK -> d.plusWeeks(dir.toLong())
                ViewMode.MONTH -> d.plusMonths(dir.toLong())
                ViewMode.YEAR -> d.plusYears(dir.toLong())
            },
        )
    }

    fun shift(days: Long) = select(_state.value.date.plusDays(days))
    fun today() = select(LocalDate.now())
    fun dismissError() = _state.update { it.copy(error = null) }

    /** Настройки поменялись — перечитать календарь (начало недели, дни рождения…). */
    fun settingsChanged() {
        backend.invalidateContacts()
        reload()
    }

    private fun go(mode: ViewMode, date: LocalDate) {
        val old = _state.value
        if (old.mode == mode && old.date == date) return
        val next = old.copy(mode = mode, date = date)
        // Сменилась страница — сразу очищаем события, чтобы не мелькнули чужие
        _state.value = if (next.pageKey != old.pageKey) next.copy(events = emptyList(), yearDays = emptySet()) else next
        if (next.pageKey != old.pageKey) reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val s = _state.value
            val (from, to) = s.range
            try {
                if (s.mode == ViewMode.YEAR) {
                    val days = withContext(ioDispatcher) { backend.daysWithEvents(from, to) }
                    _state.update { if (it.pageKey == s.pageKey) it.copy(yearDays = days) else it }
                } else {
                    val ev = withContext(ioDispatcher) { backend.eventsBetween(from, to) }
                    _state.update { if (it.pageKey == s.pageKey) it.copy(events = ev) else it }
                }
            } catch (e: CancellationException) {
                throw e // отмена старой загрузки — это не ошибка, не показываем
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    /* ---- Контакты и номера ---- */

    /* ---- Почта ---- */

    /** Люди с почтой — для «Кому» и «Написать». */
    suspend fun searchEmails(q: String): List<EmailContact> =
        withContext(ioDispatcher) { backend.searchEmails(q) }

    /** Контакт по адресу почты (чтобы письмо попало в историю человека). */
    suspend fun contactByEmail(addr: String): ContactRef? = withContext(ioDispatcher) { backend.contactByEmail(addr) }

    suspend fun searchContacts(q: String): List<ContactRef> =
        withContext(ioDispatcher) { runCatching { backend.searchContacts(q) }.getOrDefault(emptyList()) }

    /** Номера контакта и номер, выбранный для него в прошлый раз (если был). */
    suspend fun phonesFor(lookupKey: String): Pair<List<PhoneNumber>, String?> = withContext(ioDispatcher) {
        backend.phones(lookupKey) to backend.rememberedPhone(lookupKey)
    }

    /** Все адреса контакта. */
    suspend fun addressesFor(lookupKey: String): List<PhoneNumber> =
        withContext(ioDispatcher) { backend.addresses(lookupKey) }

    /** Выбрать адрес контакта (запоминается для всех его событий). */
    fun setAddress(lookupKey: String, address: String) {
        backend.rememberAddress(lookupKey, address)
        reload()
    }

    /* ---- Календарь для записи: последний использованный запоминается ---- */

    /** Все календари телефона с пометками (только чтение, без синхронизации, локальный). */
    suspend fun allCalendars(): List<CalendarInfo> =
        withContext(ioDispatcher) { runCatching { backend.allCalendars() }.getOrDefault(emptyList()) }

    /* ---- Какие календари показывать (галочки) ---- */

    private val _hidden = MutableStateFlow(
        backend.getStringSet(KEY_HIDDEN).mapNotNull { it.toLongOrNull() }.toSet(),
    )
    val hiddenCalendars = _hidden.asStateFlow()

    fun setCalendarShown(id: Long, shown: Boolean) {
        val next = if (shown) _hidden.value - id else _hidden.value + id
        _hidden.value = next
        backend.hiddenCalendars = next
        backend.putStringSet(KEY_HIDDEN, next.map { it.toString() }.toSet())
        reload()
    }

    /** Последний календарь, в который создавали событие, или null — тогда спросим. */
    fun lastCalendarId(): Long? = CurrentSettings.current.calendarFixedId
        ?: backend.getLong(KEY_LAST_CAL)?.takeIf { it >= 0 }

    /* ---- Изменения ---- */

    fun create(e: NewEvent) = launchSafe {
        backend.putLong(KEY_LAST_CAL, e.calendarId)
        val id = backend.create(e)
        // Файлы, выбранные при создании: один неудачный не мешает остальным
        e.files.forEach { f -> runCatching { withContext(ioDispatcher) { backend.addTripFile(id, f.uri, f.label) } } }
        if (e.files.isNotEmpty()) reload()
        val d = e.start.toLocalDate()
        val (from, to) = _state.value.range
        if (d < from || d >= to) select(d)
    }

    fun setLink(e: PalmEvent, type: EventType?, contact: ContactRef?) = launchSafe {
        backend.setLink(e.eventId, type, contact)
        backend.invalidateContacts()
    }

    /** История контакта: все события с ним, прошлые и будущие. */
    suspend fun history(lookupKey: String): List<PalmEvent> =
        withContext(ioDispatcher) { backend.historyFor(lookupKey) }

    /** Статистика по людям: состоявшиеся звонки и встречи с даты [from]. */
    suspend fun contactStats(from: LocalDate): List<ContactStat> =
        withContext(ioDispatcher) { backend.contactStats(from) }

    suspend fun reminders(eventId: Long): List<Int> = withContext(ioDispatcher) { backend.reminders(eventId) }

    fun setReminders(eventId: Long, minutes: List<Int>) = launchSafe { backend.setReminders(eventId, minutes) }

    /** Удалить: только этот раз, этот и следующие или всю серию (у обычного события — просто удалить). */
    fun delete(e: PalmEvent, scope: DeleteScope = DeleteScope.ALL) = launchSafe {
        when (scope) {
            DeleteScope.ONE -> backend.deleteOne(e)
            DeleteScope.FOLLOWING -> backend.deleteFollowing(e)
            DeleteScope.ALL -> backend.delete(e.eventId)
        }
    }

    /** Правка из подробностей: название, время, заметка. */
    fun setTitle(e: PalmEvent, title: String) = launchSafe { backend.setTitle(e.eventId, title) }
    fun setTime(e: PalmEvent, start: LocalDateTime, minutes: Int) = launchSafe { backend.setTime(e, start, minutes) }
    fun setNote(e: PalmEvent, note: String?) = launchSafe { backend.setNote(e.eventId, note) }

    /* ---- Выезд: адрес и документы ---- */

    /** Адрес выезда (поле «Место» календаря). */
    suspend fun place(eventId: Long): String? = withContext(ioDispatcher) { backend.location(eventId) }

    fun setPlace(eventId: Long, place: String?) = launchSafe {
        backend.setLocation(eventId, place)
        reload()   // список сразу покажет (или уберёт) булавку-маршрут
    }

    /** Документы выезда: билеты, посадочные, брони. */
    suspend fun tripFiles(eventId: Long): List<TripDoc> =
        withContext(ioDispatcher) { backend.tripFiles(eventId) }

    suspend fun addTripFile(eventId: Long, uri: String, label: String) {
        withContext(ioDispatcher) { backend.addTripFile(eventId, uri, label) }
        reload()   // значок документов в списке
    }

    suspend fun deleteTripFile(d: TripDoc) {
        withContext(ioDispatcher) { backend.deleteTripFile(d) }
        reload()
    }

    /** Сменить повтор серии события. */
    fun setRepeat(e: PalmEvent, rrule: String?) = launchSafe { backend.setRepeat(e, rrule) }

    /* ---- Итоги ---- */

    fun setOutcome(e: PalmEvent, outcome: Outcome?, note: String?) =
        launchSafe { backend.setOutcome(e, outcome, note) }

    /** Галочка задачи по тапу на иконку: выполнена ↔ без отметки. */
    fun toggleTask(e: PalmEvent) =
        setOutcome(e, if (e.outcome == Outcome.DONE) null else Outcome.DONE, e.outcomeNote)

    /**
     * Создать продолжение события в другое время — для "перезвонить" и "перенести":
     * тот же тип, контакт и номер, длительность и календарь.
     */
    fun followUp(e: PalmEvent, start: LocalDateTime) {
        val type = e.type ?: EventType.OTHER
        val minutes = if (e.allDay) 0 else minutesBetween(e.start, e.end).toInt().coerceAtLeast(5)
        val cal = e.calendarId.takeIf { it >= 0 } ?: lastCalendarId() ?: return
        create(
            NewEvent(
                type = type,
                contact = e.contact,
                title = if (e.type == null) e.title else null,
                start = start,
                minutes = minutes,
                note = null,
                calendarId = cal,
                reminders = if (minutes == 0) CurrentSettings.current.allDayReminders
                else CurrentSettings.current.remindersFor(type),
                mail = e.mail?.copy(hasDraft = false),
            ),
        )
    }

    /** Перенести событие в другой календарь (копия + удаление оригинала). */
    fun move(e: PalmEvent, calendarId: Long) = launchSafe { backend.move(e.eventId, calendarId) }

    /** Выполнить изменение в фоне, показать ошибку, если что-то пошло не так, и перечитать. */
    private fun launchSafe(block: suspend () -> Unit) = scope.launch {
        try {
            withContext(ioDispatcher) { block() }
        } catch (c: CancellationException) {
            throw c
        } catch (err: Exception) {
            _state.update { it.copy(error = err.message) }
        }
        reload()
    }

    private companion object {
        const val KEY_LAST_CAL = "last_calendar_id"
        const val KEY_MODE = "view_mode"
        const val KEY_HIDDEN = "hidden_calendars"
    }
}
