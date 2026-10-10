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

/** Начало недели — из настроек: понедельник или воскресенье. */
fun firstDayOfWeek(): DayOfWeek =
    if (ru.palmdate.app.data.SettingsStore.current.weekStartsSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY

fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))
fun monthGridStart(d: LocalDate): LocalDate = weekStart(d.withDayOfMonth(1))

class DayViewModel(app: Application) : AndroidViewModel(app) {
    private val contacts = ContactsRepository(app)
    private val db = AppDb.get(app)
    private val repo = CalendarRepository(app, db.links(), contacts)
    private val trips = ru.palmdate.app.data.TripFiles(app, db.links())
    private val prefs = app.getSharedPreferences("palmdate", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        CalState(
            mode = when (ru.palmdate.app.data.SettingsStore.also { it.init(app) }.current.startView) {
                ru.palmdate.app.data.StartView.DAY -> ViewMode.DAY
                ru.palmdate.app.data.StartView.AGENDA -> ViewMode.AGENDA
                ru.palmdate.app.data.StartView.LAST ->
                    runCatching { ViewMode.valueOf(prefs.getString(KEY_MODE, null)!!) }.getOrDefault(ViewMode.DAY)
            },
        ),
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

    /* ---- Резервная копия, почта, обновления ---- */

    private val backup = ru.palmdate.app.data.Backup(app, db.links())

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) { backup.export() }

    suspend fun importBackup(text: String): Int = withContext(Dispatchers.IO) { backup.import(text) }
        .also { settingsChanged() }

    suspend fun checkMail(password: String): String? = withContext(Dispatchers.IO) {
        runCatching { ru.palmdate.app.data.Mail.check(ru.palmdate.app.data.SettingsStore.current, password) }
            .getOrElse { it.message ?: "Ошибка" }
    }

    /** Номер последней сборки на GitHub (или null, если не удалось узнать). */
    suspend fun latestBuild(): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val c = java.net.URL("https://api.github.com/repos/031168-sudo/Palm-calendar/releases/latest")
                .openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            org.json.JSONObject(body).getString("tag_name").removePrefix("build-").toInt()
        }.getOrNull()
    }

    /** Настройки поменялись — перечитать календарь (начало недели, дни рождения…). */
    fun settingsChanged() {
        contacts.invalidate()
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

    /* ---- Почта ---- */

    /** Люди с почтой — для «Кому» и «Написать». */
    suspend fun searchEmails(q: String): List<ru.palmdate.app.data.ContactsRepository.EmailContact> =
        withContext(Dispatchers.IO) { contacts.searchEmails(q) }

    /** Контакт по адресу почты (чтобы письмо попало в историю человека). */
    suspend fun contactByEmail(addr: String): ContactRef? = withContext(Dispatchers.IO) { contacts.byEmail(addr) }

    /** Письмо события с черновиком. */
    suspend fun mailLink(eventId: Long): ru.palmdate.app.data.MailLink? = withContext(Dispatchers.IO) { repo.mailLink(eventId) }

    /** Сохранить черновик: в «Черновики» ящика и у события. */
    suspend fun saveMailDraft(eventId: Long, o: ru.palmdate.app.data.Outgoing) {
        val old = repo.mailLink(eventId)?.draftId
        val id = runCatching { ru.palmdate.app.data.MailClient.saveDraft(getApplication(), o, old) }.getOrNull() ?: old
        withContext(Dispatchers.IO) { repo.setMailDraft(eventId, o.toJson(), id) }
        reload()
    }

    /** Отказаться от письма: черновик убрать отовсюду. */
    suspend fun dropMailDraft(eventId: Long) {
        val old = repo.mailLink(eventId)?.draftId
        old?.let { ru.palmdate.app.data.MailClient.deleteDraft(it) }
        withContext(Dispatchers.IO) { repo.setMailDraft(eventId, null, null) }
        reload()
    }

    /** Отправить письмо события: черновик убрать, итог — «Отправлено». */
    suspend fun sendMail(e: PalmEvent?, o: ru.palmdate.app.data.Outgoing) {
        ru.palmdate.app.data.MailClient.send(getApplication(), o)
        if (e != null) {
            runCatching { dropMailDraft(e.eventId) }
            withContext(Dispatchers.IO) { repo.setOutcome(e, Outcome.DONE, e.outcomeNote) }
            reload()
        }
    }

    suspend fun searchContacts(q: String): List<ContactRef> =
        withContext(Dispatchers.IO) { contacts.search(q) }

    /** Номера контакта и номер, выбранный для него в прошлый раз (если был). */
    suspend fun phonesFor(lookupKey: String): Pair<List<PhoneNumber>, String?> = withContext(Dispatchers.IO) {
        contacts.phones(lookupKey) to db.links().rememberedPhone(lookupKey)
    }

    /** Все адреса контакта. */
    suspend fun addressesFor(lookupKey: String): List<PhoneNumber> =
        withContext(Dispatchers.IO) { contacts.addresses(lookupKey) }

    /** Выбрать адрес контакта (запоминается для всех его событий). */
    fun setAddress(lookupKey: String, address: String) {
        contacts.rememberAddress(lookupKey, address)
        reload()
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
    fun lastCalendarId(): Long? = ru.palmdate.app.data.SettingsStore.current.calendarFixedId
        ?: prefs.getLong(KEY_LAST_CAL, -1L).takeIf { it >= 0 }

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

    /** Статистика по людям: состоявшиеся звонки и встречи с даты [from]. */
    suspend fun contactStats(from: LocalDate): List<ru.palmdate.app.model.ContactStat> =
        withContext(Dispatchers.IO) { repo.contactStats(from) }

    suspend fun reminders(eventId: Long): List<Int> = withContext(Dispatchers.IO) { repo.reminders(eventId) }

    fun setReminders(eventId: Long, minutes: List<Int>) = launchSafe { repo.setReminders(eventId, minutes) }

    /** Удалить: только этот раз, этот и следующие или всю серию (у обычного события — просто удалить). */
    fun delete(e: PalmEvent, scope: ru.palmdate.app.model.DeleteScope = ru.palmdate.app.model.DeleteScope.ALL) = launchSafe {
        when (scope) {
            ru.palmdate.app.model.DeleteScope.ONE -> repo.deleteOne(e)
            ru.palmdate.app.model.DeleteScope.FOLLOWING -> repo.deleteFollowing(e)
            ru.palmdate.app.model.DeleteScope.ALL -> repo.delete(e.eventId)
        }
    }

    /** Правка из подробностей: название, время, заметка. */
    fun setTitle(e: PalmEvent, title: String) = launchSafe { repo.setTitle(e.eventId, title) }
    fun setTime(e: PalmEvent, start: LocalDateTime, minutes: Int) = launchSafe { repo.setTime(e, start, minutes) }
    fun setNote(e: PalmEvent, note: String?) = launchSafe { repo.setNote(e.eventId, note) }

    /* ---- Выезд: адрес и документы ---- */

    /** Адрес выезда (поле «Место» календаря). */
    suspend fun place(eventId: Long): String? = withContext(Dispatchers.IO) { repo.location(eventId) }

    fun setPlace(eventId: Long, place: String?) = launchSafe {
        repo.setLocation(eventId, place)
        withContext(Dispatchers.Main) { reload() }   // список сразу покажет (или уберёт) булавку-маршрут
    }

    /** Документы выезда: билеты, посадочные, брони. */
    suspend fun tripFiles(eventId: Long): List<ru.palmdate.app.data.TripFile> =
        withContext(Dispatchers.IO) { trips.list(eventId) }

    suspend fun addTripFile(eventId: Long, uri: android.net.Uri, label: String) {
        withContext(Dispatchers.IO) { trips.add(eventId, uri, label) }
        withContext(Dispatchers.Main) { reload() }   // значок документов в списке
    }

    suspend fun deleteTripFile(f: ru.palmdate.app.data.TripFile) {
        withContext(Dispatchers.IO) { trips.delete(f) }
        withContext(Dispatchers.Main) { reload() }
    }

    /** Сменить повтор серии события. */
    fun setRepeat(e: PalmEvent, rrule: String?) = launchSafe { repo.setRepeat(e, rrule) }

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
                reminders = if (minutes == 0) ru.palmdate.app.data.SettingsStore.current.allDayReminders
                else ru.palmdate.app.data.SettingsStore.current.remindersFor(type),
                mail = e.mail?.copy(hasDraft = false),
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
