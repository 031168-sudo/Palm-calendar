package ru.palmdate.app.ui

import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.MailKind
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.str

/** Что делает тап по иконке события — как на Palm: звонок набирает номер, встреча ведёт на карту. */
fun primaryActionLabel(e: PalmEvent): String? = when {
    e.type == EventType.MAIL && e.mail != null ->
        if (e.mail?.kind == MailKind.REPLY) str("action_find_mail") else str("action_write_mail")
    e.fromContacts && e.contact != null -> str("action_congratulate")
    e.type == EventType.CALL && e.contact != null -> str("action_call")
    e.type == EventType.MEETING && (e.place ?: e.contact?.address) != null -> str("action_route")
    e.type == EventType.TRIP && e.place != null -> str("action_route")
    (e.type == EventType.OTHER || e.type == null) && !e.fromContacts && e.place != null -> str("action_route")
    else -> null
}

