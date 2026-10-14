package ru.palmdate.app.ui

import ru.palmdate.app.str
import ru.palmdate.app.plu
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.icon
import ru.palmdate.app.model.color
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.OutcomeKind
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.ui.theme.Palm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import ru.palmdate.app.time.*

private fun daysAgo(d: LocalDate): String {
    val n = d.daysUntil(LocalDate.now())
    return when (n) {
        0 -> str("today")
        1 -> str("yesterday")
        else -> plu("count_days_ago", n)
    }
}

private fun daysAhead(d: LocalDate): String {
    val n = LocalDate.now().daysUntil(d)
    return when (n) {
        0 -> str("today")
        1 -> str("tomorrow")
        else -> plu("count_in_days", n)
    }
}

private fun typeCount(events: List<PalmEvent>): String {
    val calls = events.count { it.type == EventType.CALL }
    val meetings = events.count { it.type == EventType.MEETING }
    val mails = events.count { it.type == EventType.MAIL }
    val other = events.size - calls - meetings - mails
    return listOfNotNull(
        calls.takeIf { it > 0 }?.let { plu("count_calls", it) },
        meetings.takeIf { it > 0 }?.let { plu("count_meetings", it) },
        mails.takeIf { it > 0 }?.let { plu("count_mails", it) },
        other.takeIf { it > 0 }?.let { plu("count_other", it) },
    ).joinToString(", ").ifEmpty { str("hist_nothing") }
}

/** Итоги писем: "отправлено 2, не отправлено 1, без отметки 1". */
private fun mailCount(events: List<PalmEvent>): String {
    val sent = events.count { it.outcome == Outcome.DONE }
    val notSent = events.count { it.outcome != null && it.outcome != Outcome.DONE }
    val none = events.count { it.outcome == null }
    return listOfNotNull(
        sent.takeIf { it > 0 }?.let { str("hist_sent", it) },
        notSent.takeIf { it > 0 }?.let { str("hist_not_sent", it) },
        none.takeIf { it > 0 }?.let { str("hist_no_outcome", it) },
    ).joinToString(", ")
}

/** "состоялось 3, не дозвонился 2, без отметки 1". Письма сюда не входят — см. mailCount. */
private fun outcomeCount(events: List<PalmEvent>): String {
    val done = events.count { it.outcome == Outcome.DONE }
    val noAnswer = events.count { it.outcome == Outcome.NO_ANSWER }
    val moved = events.count { it.outcome == Outcome.RESCHEDULED }
    val failed = events.count { it.outcome?.kind == OutcomeKind.BAD } - noAnswer
    val none = events.count { it.outcome == null }
    return listOfNotNull(
        done.takeIf { it > 0 }?.let { str("hist_done", it) },
        noAnswer.takeIf { it > 0 }?.let { str("hist_no_answer", it) },
        moved.takeIf { it > 0 }?.let { str("hist_moved", it) },
        failed.takeIf { it > 0 }?.let { str("hist_failed", it) },
        none.takeIf { it > 0 }?.let { str("hist_no_outcome", it) },
    ).joinToString(", ")
}

private sealed interface HRow {
    data class Section(val title: String) : HRow
    data class Month(val title: String) : HRow
    data class Item(val e: PalmEvent) : HRow
}

/**
 * История контакта — как в Agendus: все звонки и встречи с человеком.
 * Сверху сводка, ниже предстоящие и прошедшие события по месяцам.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactHistorySheet(
    contact: ContactRef,
    load: suspend (String) -> List<PalmEvent>,
    onEvent: (PalmEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var events by remember { mutableStateOf<List<PalmEvent>?>(null) }
    LaunchedEffect(contact.lookupKey) { events = load(contact.lookupKey) }

    PalmSheet(onDismissRequest = onDismiss, fixedHeight = false) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // Шапка: инициал, имя, кнопка звонка
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Palm.navyLight),
                    contentAlignment = Alignment.Center,
                ) { Text(contact.name.take(1).uppercase(), style = Palm.title, color = Palm.navy) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(contact.name, style = Palm.title, color = Palm.ink, maxLines = 1)
                    contact.phone?.let { Text(it, style = Palm.small, color = Palm.inkSoft) }
                }
                val call = LocalCaller.current
                PalmButton(str("action_call"), filled = true) { call(contact) }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))
        }

        val list = events
        if (list == null) {
            Text(str("loading"), style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(16.dp))
            return@PalmSheet
        }
        if (list.isEmpty()) {
            Text(
                str("hist_empty"),
                style = Palm.body, color = Palm.inkSoft, modifier = Modifier.padding(16.dp),
            )
            return@PalmSheet
        }

        val now = LocalDateTime.now()
        val past = list.filter { !it.start.isAfter(now) }.sortedByDescending { it.start }
        val future = list.filter { it.start.isAfter(now) }.sortedBy { it.start }
        val today = LocalDate.now()

        // Сводка
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            past.firstOrNull()?.let { e ->
                val res = e.outcome?.let { ", " + it.label(e.type).lowercase() } ?: ""
                Summary(str("hist_last"), "${(e.typeLabel ?: str("type_other")).lowercase()}, ${daysAgo(e.start.toLocalDate())}$res")
            } ?: Summary(str("hist_last"), str("hist_none_yet"))
            // Последний состоявшийся — если последний был неудачным
            if (past.firstOrNull()?.outcome != Outcome.DONE) {
                past.firstOrNull { it.outcome == Outcome.DONE }?.let { e ->
                    Summary(str("hist_completed"), "${(e.typeLabel ?: str("type_other")).lowercase()}, ${daysAgo(e.start.toLocalDate())}")
                }
            }
            future.firstOrNull()?.let { e ->
                Summary(str("hist_next"), "${(e.typeLabel ?: str("type_other")).lowercase()}, ${daysAhead(e.start.toLocalDate())}")
            }
            Summary(str("hist_30_days"), typeCount(past.filter { it.start.toLocalDate() >= today.minusDays(30) }))
            val year = past.filter { it.start.toLocalDate() >= today.minusYears(1) }
            Summary(str("hist_year"), typeCount(year))
            val yearNoMail = year.filter { it.type != EventType.MAIL }
            if (yearNoMail.any { it.outcome != null }) Summary(str("hist_year_outcomes"), outcomeCount(yearNoMail))
            val yearMail = year.filter { it.type == EventType.MAIL }
            if (yearMail.isNotEmpty()) Summary(str("hist_year_mails"), mailCount(yearMail))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Palm.rule))

        // Список: предстоящие, затем прошедшие — по месяцам
        val rows = buildList {
            fun addGroup(title: String, items: List<PalmEvent>) {
                if (items.isEmpty()) return
                add(HRow.Section(title))
                var lastMonth: String? = null
                items.forEach { e ->
                    val d = e.start.toLocalDate()
                    val m = d.monthTitle() + (if (d.year != today.year) " ${d.year}" else "")
                    if (m != lastMonth) { add(HRow.Month(m)); lastMonth = m }
                    add(HRow.Item(e))
                }
            }
            addGroup(str("hist_upcoming"), future)
            addGroup(str("hist_past"), past)
        }

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(bottom = 16.dp)) {
            items(rows) { r ->
                when (r) {
                    is HRow.Section -> Text(
                        r.title, style = Palm.button, color = Palm.navy,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
                    )
                    is HRow.Month -> Text(
                        r.title, style = Palm.small, color = Palm.inkSoft,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                    )
                    is HRow.Item -> {
                        val d = r.e.start.toLocalDate()
                        val dow = dayOfWeekName(d.dayOfWeek, NameStyle.SHORT)
                        val title = (if (r.e.type == EventType.MAIL) r.e.displayTitle else r.e.typeLabel ?: r.e.title) +
                            if (r.e.allDay) "" else ", " + r.e.start.format(HM)
                        EventLine(
                            r.e, "${d.dayOfMonth} $dow", highlight = d == today,
                            onIcon = { onEvent(it) }, onEvent = onEvent, primary = title,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(label: String, value: String) {
    Row {
        Text(label, style = Palm.small, color = Palm.inkSoft, modifier = Modifier.width(96.dp))
        Text(value, style = Palm.body, color = Palm.ink)
    }
}
