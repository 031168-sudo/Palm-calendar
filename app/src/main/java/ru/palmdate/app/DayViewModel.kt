package ru.palmdate.app

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.palmdate.app.data.AppDb
import ru.palmdate.app.data.CalendarRepository
import ru.palmdate.app.data.ContactsRepository
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.NewEvent
import ru.palmdate.app.model.Outcome
import java.time.LocalDateTime
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.model.PhoneNumber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Виды, как на Palm: переключаются иконками в нижней панели. */
enum class ViewMode(val label: String) {
    AGENDA("Повестка"), DAY("День"), WEEK("Неделя"), MONTH("Месяц"), YEAR("Год")
}

data class CalState(
    val mode: ViewMode = ViewMode.DAY,
    val date: LocalDate = LocalDate.now(),
    val events: List<PalmEvent> = emptyList(),
    val yearDays: Set<LocalDate> = emptySet(),
    val error: String? = null,
) {
    /** Начало и конец (не включительно) загружаемого диапазона. */
    val range: Pair<LocalDate, LocalDate>
        get() = when (mode) {
            ViewMode.DAY -> date to date.plusDays(1)
            ViewMode.AGENDA -> date to date.plusDays(7)
            ViewMode.WEEK -> weekStart(date).let { it to it.plusDays(7) }
            ViewMode.MONTH -> monthGridStart(date).let { it to it.plusDays(42) }
            ViewMode.YEAR -> date.withDayOfYear(1).let { it to it.plusYears(1) }
        }

    /** Ключ "страницы": меняется — экран уезжает свайпом. */
    val pageKey: String get() = "$mode:${range.first}"
}

fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
fun monthGridStart(d: LocalDate): LocalDate = weekStart(d.withDayOfMonth(1))

class DayViewModel(app: Application) : AndroidViewModel(app) {
    private val contacts = ContactsRepository(app)
    private val db = AppDb.get(app)
    private val repo = CalendarRepository(app, db.links(), contacts)
    private val prefs = app.getSharedPreferences("palmdate", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        CalState(mode = runCatching { ViewMode.valueOf(prefs.getString(KEY_MODE, null)!!) }.getOrDefault(ViewMode.DAY)),
    )
    val state = _state.asStateFlow()

    private var started = false
    private var loadJob: Job? = null

    // Календарь могут поменять снаружи (Google sync, другое приложение) — перечитываем.
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload()
    }

    /** Вызывается после выдачи разрешений. */
    fun start() {
        if (started) return
        started = true
        repo.hiddenCalendars = _hidden.value
        contacts.invalidate()
        getApplication<Application>().contentResolver
            .registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
        reload()
    }

    /* ---- Навигация ---- */

    fun select(date: LocalDate) = go(_state.value.mode, date)

    fun setMode(mode: ViewMode, date: LocalDate = _state.value.date) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
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
        loadJob = viewModelScope.launch {
            val s = _state.value
            val (from, to) = s.range
            try {
                if (s.mode == ViewMode.YEAR) {
                    val days = withContext(Dispatchers.IO) { repo.daysWithEvents(from, to) }
                    _state.update { if (it.pageKey == s.pageKey) it.copy(yearDays = days) else it }
                } else {
                    val ev = withContext(Dispatchers.IO) { repo.eventsBetween(from, to) }
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

    suspend fun searchContacts(q: String): List<ContactRef> =
        withContext(Dispatchers.IO) { contacts.search(q) }

    /** Номера контакта и номер, выбранный для него в прошлый раз (если был). */
    suspend fun phonesFor(lookupKey: String): Pair<List<PhoneNumber>, String?> = withContext(Dispatchers.IO) {
        contacts.phones(lookupKey) to db.links().rememberedPhone(lookupKey)
    }

    /* ---- Календарь для записи: последний использованный запоминается ---- */

    /** Все календари телефона с пометками (только чтение, без синхронизации, локальный). */
    suspend fun allCalendars(): List<CalendarInfo> =
        withContext(Dispatchers.IO) { repo.allCalendars() }

    /* ---- Какие календари показывать (галочки) ---- */

    private val _hidden = MutableStateFlow(
        prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
    )
    val hiddenCalendars = _hidden.asStateFlow()

    fun setCalendarShown(id: Long, shown: Boolean) {
        val next = if (shown) _hidden.value - id else _hidden.value + id
        _hidden.value = next
        repo.hiddenCalendars = next
        prefs.edit().putStringSet(KEY_HIDDEN, next.map { it.toString() }.toSet()).apply()
        reload()
    }

    /** Последний календарь, в который создавали событие, или null — тогда спросим. */
    fun lastCalendarId(): Long? = prefs.getLong(KEY_LAST_CAL, -1L).takeIf { it >= 0 }

    /* ---- Изменения ---- */

    fun create(e: NewEvent) = launchSafe {
        prefs.edit().putLong(KEY_LAST_CAL, e.calendarId).apply()
        repo.create(e)
        val d = e.start.toLocalDate()
        val (from, to) = _state.value.range
        if (d < from || d >= to) select(d)
    }

    fun setLink(e: PalmEvent, type: EventType?, contact: ContactRef?) = launchSafe {
        repo.setLink(e.eventId, type, contact)
        contacts.invalidate()
    }

    /** История контакта: все события с ним, прошлые и будущие. */
    suspend fun history(lookupKey: String): List<PalmEvent> =
        withContext(Dispatchers.IO) { repo.historyFor(lookupKey) }

    suspend fun reminders(eventId: Long): List<Int> = withContext(Dispatchers.IO) { repo.reminders(eventId) }

    fun setReminders(eventId: Long, minutes: List<Int>) = launchSafe { repo.setReminders(eventId, minutes) }

    fun delete(e: PalmEvent) = launchSafe { repo.delete(e.eventId) }

    /* ---- Итоги ---- */

    fun setOutcome(e: PalmEvent, outcome: Outcome?, note: String?) =
        launchSafe { repo.setOutcome(e, outcome, note) }

    /** Галочка задачи по тапу на иконку: выполнена ↔ без отметки. */
    fun toggleTask(e: PalmEvent) =
        setOutcome(e, if (e.outcome == Outcome.DONE) null else Outcome.DONE, e.outcomeNote)

    /**
     * Создать продолжение события в другое время — для "перезвонить" и "перенести":
     * тот же тип, контакт и номер, длительность и календарь.
     */
    fun followUp(e: PalmEvent, start: LocalDateTime) {
        val type = e.type ?: EventType.OTHER
        val minutes = if (e.allDay) 0 else java.time.Duration.between(e.start, e.end).toMinutes().toInt().coerceAtLeast(5)
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
                reminders = type.defaultReminders,
            ),
        )
    }

    /** Перенести событие в другой календарь (копия + удаление оригинала). */
    fun move(e: PalmEvent, calendarId: Long) = launchSafe { repo.move(e.eventId, calendarId) }

    /** Выполнить изменение в фоне, показать ошибку, если что-то пошло не так, и перечитать. */
    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { block() }
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

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
