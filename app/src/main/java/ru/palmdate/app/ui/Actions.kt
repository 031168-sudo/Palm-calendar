package ru.palmdate.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import ru.palmdate.app.data.CalendarRepository
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.PalmEvent

/** Что делает тап по иконке события — как на Palm: звонок набирает номер, встреча ведёт на карту. */
fun primaryActionLabel(e: PalmEvent): String? = when {
    e.fromContacts && e.contact?.phone != null -> "Поздравить"
    e.type == EventType.CALL && e.contact?.phone != null -> "Позвонить"
    e.type == EventType.MEETING && e.contact?.address != null -> "Маршрут"
    else -> null
}

fun Context.runPrimaryAction(e: PalmEvent) {
    val c = e.contact
    when {
        // День рождения из контактов — позвонить имениннику
        e.fromContacts -> c?.phone?.let { dial(it) }
        e.type == EventType.CALL && c?.phone != null ->
            launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.phone))))
        e.type == EventType.MEETING && c?.address != null ->
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(c.address))))
        else -> openInCalendar(e)
    }
}

fun Context.dial(number: String) =
    launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

fun Context.openInCalendar(e: PalmEvent) =
    launch(Intent(Intent.ACTION_VIEW, CalendarRepository.eventUri(e.eventId)))

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "Нет приложения для этого действия", Toast.LENGTH_SHORT).show()
    }
}
