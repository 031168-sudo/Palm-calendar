package ru.palmdate.app.ui

import ru.palmdate.app.R
import ru.palmdate.app.str
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
    e.type == EventType.MAIL && e.mail != null -> {
        val builtin = ru.palmdate.app.data.SettingsStore.current.mailMode == ru.palmdate.app.data.MailMode.BUILTIN
        when {
            e.mail.kind == ru.palmdate.app.model.MailKind.REPLY ->
                if (builtin && e.mail.messageId != null) str(R.string.action_open_mail) else str(R.string.action_find_mail)
            builtin && e.mail.hasDraft -> str(R.string.action_continue_mail)
            else -> str(R.string.action_write_mail)
        }
    }
    e.fromContacts && e.contact != null -> str(R.string.action_congratulate)
    e.type == EventType.CALL && e.contact != null -> str(R.string.action_call)
    e.type == EventType.MEETING && e.contact?.address != null -> str(R.string.action_route)
    e.type == EventType.TRIP && e.place != null -> str(R.string.action_route)
    else -> null
}

/** call — показать выбор "телефон или мессенджер" (LocalCaller); mail — открыть письмо (LocalMailer). */
fun Context.runPrimaryAction(e: PalmEvent, call: (ru.palmdate.app.model.ContactRef) -> Unit, mail: (PalmEvent) -> Unit = {}) {
    val c = e.contact
    when {
        e.type == EventType.MAIL && e.mail != null -> mail(e)
        // День рождения из контактов — поздравить: телефон или мессенджер
        e.fromContacts -> c?.let(call)
        e.type == EventType.CALL && c != null -> call(c)
        e.type == EventType.MEETING && c?.address != null -> openMap(c.address)
        // Выезд с адресом — сразу маршрут; без адреса — как раньше, карточка в календаре
        e.type == EventType.TRIP && e.place != null -> e.place?.let { openMap(it) }
        else -> openInCalendar(e)
    }
}

/** Открыть адрес в картах — с выбором приложения (Яндекс, Google, 2ГИС…). */
fun Context.openMap(address: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)))
    launch(Intent.createChooser(intent, str(R.string.action_open_maps)))
}

/** Обычный набор номера (без выбора мессенджера). */
fun Context.dial(number: String) =
    launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

fun Context.openInCalendar(e: PalmEvent) =
    launch(Intent(Intent.ACTION_VIEW, CalendarRepository.eventUri(e.eventId)))

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, str(R.string.err_no_app), Toast.LENGTH_SHORT).show()
    }
}
