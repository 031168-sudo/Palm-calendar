package ru.palmdate.app

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.NewEvent
import ru.palmdate.app.model.PalmEvent
import java.time.LocalDate

data class DayState(
    val date: LocalDate = LocalDate.now(),
    val events: List<PalmEvent> = emptyList(),
    val error: String? = null,
)

class DayViewModel(app: Application) : AndroidViewModel(app) {
    private val contacts = ContactsRepository(app)
    private val repo = CalendarRepository(app, AppDb.get(app).links(), contacts)

    private val _state = MutableStateFlow(DayState())
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
        getApplication<Application>().contentResolver
            .registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
        reload()
    }

    fun select(date: LocalDate) {
        _state.update { it.copy(date = date) }
        reload()
    }

    fun shift(days: Long) = select(_state.value.date.plusDays(days))
    fun today() = select(LocalDate.now())
    fun dismissError() = _state.update { it.copy(error = null) }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val date = _state.value.date
            runCatching { withContext(Dispatchers.IO) { repo.eventsFor(date) } }
                .onSuccess { ev -> _state.update { if (it.date == date) it.copy(events = ev) else it } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    suspend fun searchContacts(q: String): List<ContactRef> =
        withContext(Dispatchers.IO) { contacts.search(q) }

    fun create(e: NewEvent) = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { repo.create(e) } }
            .onSuccess {
                if (e.start.toLocalDate() != _state.value.date) select(e.start.toLocalDate()) else reload()
            }
            .onFailure { err -> _state.update { it.copy(error = err.message) } }
    }

    fun delete(e: PalmEvent) = viewModelScope.launch {
        withContext(Dispatchers.IO) { repo.delete(e.eventId) }
        reload()
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
