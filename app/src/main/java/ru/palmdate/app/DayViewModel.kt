package ru.palmdate.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.palmdate.app.data.AndroidBackend
import ru.palmdate.app.data.AppDb
import ru.palmdate.app.data.CalendarRepository
import ru.palmdate.app.data.ContactsRepository

/**
 * Android-обёртка над общей моделью экрана [DayModel]: создаёт календарь/контакты/базу,
 * отдаёт то, что только для Android (почтовые черновики, резервная копия, проверка обновлений).
 */
class DayViewModel(app: Application) : AndroidViewModel(app) {
    private val contacts = ContactsRepository(app)
    private val db = AppDb.get(app)
    private val repo = CalendarRepository(app, db.links(), contacts)
    private val backend = AndroidBackend(app, contacts, db, repo, ru.palmdate.app.data.TripFiles(app, db.links()))
    private val backup = ru.palmdate.app.data.Backup(app, db.links())

    /** Общая модель: состояние и действия. */
    val model: DayModel = ru.palmdate.app.data.SettingsStore.also { it.init(app) }.let { DayModel(viewModelScope, backend) }

    fun settingsChanged() = model.settingsChanged()
    fun reload() = model.reload()

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) { backup.export() }

    suspend fun importBackup(text: String): Int = withContext(Dispatchers.IO) { backup.import(text) }
        .also { settingsChanged() }

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

    /** Письмо события с черновиком. */
    suspend fun mailLink(eventId: Long): ru.palmdate.app.data.MailLink? = withContext(Dispatchers.IO) { repo.mailLink(eventId) }

    override fun onCleared() = backend.close()
}
