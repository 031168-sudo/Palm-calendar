package ru.palmdate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DisabledByDefault
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.MailKind
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.OutcomeKind
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.model.color
import ru.palmdate.app.model.icon
import ru.palmdate.app.str
import ru.palmdate.app.time.*
import ru.palmdate.app.ui.theme.Palm

@Composable
fun TimeLabel(text: String, highlight: Boolean) {
    Text(
        text,
        style = Palm.time,
        color = if (highlight) Palm.nowLine else Palm.inkSoft,
        textAlign = TextAlign.End,
        modifier = Modifier.width(52.dp).padding(end = 10.dp),
    )
}


/** Отрезок длительности события в одной строке: доли высоты строки, дорожка, где начинается/кончается. */
class LaneSpan(val lane: Int, val color: Color, val from: Float, val to: Float, val startsHere: Boolean, val endsHere: Boolean)

/** Дорожки длительности дня: отрезки этой строки, число дорожек, заливка. */
class LaneDecor(val spans: List<LaneSpan>, val lanes: Int, val band: Boolean) {
    val gutter get() = if (lanes == 0) 0.dp else (lanes * 6 + 4).dp
}

/** Рисует за строкой: бледную заливку на ширину строки и вертикальные линии длительности в своей дорожке. */
fun Modifier.laneDraw(d: LaneDecor?): Modifier = if (d == null || d.spans.isEmpty()) this else drawBehind {
    val labelW = 52.dp.toPx()
    d.spans.forEach { sp ->
        val y0 = sp.from * size.height
        val y1 = sp.to * size.height
        if (d.band) {
            drawRect(
                sp.color.copy(alpha = Palm.tintAlpha * 0.9f),
                Offset(labelW, y0), Size(size.width - labelW, y1 - y0),
            )
        }
        val x = labelW + 2.dp.toPx() + sp.lane * 6.dp.toPx() + 3.dp.toPx()
        drawLine(sp.color, Offset(x, y0), Offset(x, y1), strokeWidth = 3.dp.toPx())
        if (sp.startsHere) drawCircle(sp.color, 3.dp.toPx(), Offset(x, y0 + 3.dp.toPx()))
        if (sp.endsHere) drawCircle(sp.color, 3.dp.toPx(), Offset(x, y1 - 3.dp.toPx()))
    }
}

/** Бледная подсветка цветом календаря — как пастельные категории на Palm. */
fun calTint(color: Int) = Color(color).copy(alpha = Palm.tintAlpha)

/**
 * Строка события: время · [полоска календаря | иконка типа | имя и подпись].
 * Иконка — в цвете типа, полоска и подсветка — в цвете календаря Google.
 */
@Composable
fun EventLine(
    e: PalmEvent,
    timeLabel: String,
    highlight: Boolean,
    onIcon: (PalmEvent) -> Unit,
    onEvent: (PalmEvent) -> Unit,
    primary: String? = null, // свой заголовок строки (в истории контакта имя не нужно)
    ruleColor: Color = Palm.rule,
    decor: LaneDecor? = null,
    titleLines: Int = 1, // только «День»: при малом числе событий название может занимать больше строк
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).laneDraw(decor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeLabel(timeLabel, highlight)
        decor?.let { Spacer(Modifier.width(it.gutter)) }
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .padding(end = 12.dp)
                .dottedRule(ruleColor),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val faded = e.outcome == Outcome.CANCELLED
            Row(
                Modifier
                    .weight(1f)
                    .padding(vertical = 3.dp)
                    .height(IntrinsicSize.Min)
                    .alpha(if (faded) 0.5f else 1f)
                    .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                    .background(calTint(e.color)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Полоска календаря
                Box(Modifier.width(3.dp).fillMaxHeight().background(Color(e.color)))
                // Иконка типа — тап по ней сразу выполняет действие (позвонить / маршрут / отметить задачу)
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { onIcon(e) },
                    contentAlignment = Alignment.Center,
                ) {
                    val type = e.type
                    when {
                        // Задача — чекбокс, как в Palm To Do
                        type == EventType.TASK -> Icon(
                            when (e.outcome) {
                                Outcome.DONE -> Icons.Outlined.CheckBox
                                Outcome.NOT_DONE -> Icons.Outlined.DisabledByDefault
                                else -> Icons.Outlined.CheckBoxOutlineBlank
                            },
                            type.label, tint = type.color, modifier = Modifier.size(22.dp),
                        )
                        e.fromContacts -> Icon(Icons.Outlined.Cake, str("type_birthday"), tint = EventType.BIRTHDAY.color, modifier = Modifier.size(20.dp))
                        type != null -> Icon(type.icon, type.label, tint = type.color, modifier = Modifier.size(20.dp))
                        else -> Box(Modifier.size(9.dp).clip(CircleShape).background(Color(e.color)))
                    }
                    if (type != EventType.TASK) e.outcome?.let { OutcomeBadge(it, Modifier.align(Alignment.BottomEnd)) }
                }
                Column(
                    Modifier.weight(1f).clickable { onEvent(e) }.padding(vertical = 5.dp).padding(end = 6.dp),
                ) {
                    val struck = e.outcome == Outcome.CANCELLED || (e.type == EventType.TASK && e.outcome == Outcome.DONE)
                    Text(
                        primary ?: (if (e.type == EventType.MAIL) e.displayTitle else e.contact?.name ?: e.displayTitle),
                        style = Palm.body.copy(textDecoration = if (struck) TextDecoration.LineThrough else null),
                        color = if (struck) Palm.inkSoft else Palm.ink,
                        maxLines = titleLines, overflow = TextOverflow.Ellipsis,
                    )
                    val sub = buildList {
                        // Итог важнее типа: "Не дозвонился — перезвонить после обеда"
                        e.outcome?.let { o -> add(o.label(e.type) + (e.outcomeNote?.let { " — $it" } ?: "")) }
                        if (primary == null && e.outcome == null) {
                            // У письма вместо типа — от кого / кому
                            val m = e.mail
                            if (e.type == EventType.MAIL && m?.peer != null) {
                                add(str(if (m.kind == MailKind.REPLY) "mail_from" else "mail_to_peer", m.peer ?: ""))
                            } else e.typeLabel?.let { add(it) }
                        }
                        if (!e.allDay && e.end.isAfter(e.start)) add(str("until_time", e.end.format(HM)))
                        if (e.outcome == null) e.note?.let { add(it.lineSequence().first()) }
                    }.joinToString(" · ")
                    val repeats = e.recurring && !e.fromContacts
                    if (sub.isNotEmpty() || e.hasFiles || e.hasAlarm != null || repeats) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Значки в начале второй строки: колокольчик, повтор, документы
                            e.hasAlarm?.let { on ->
                                Icon(
                                    if (on) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                                    str(if (on) "has_reminder" else "no_reminder"),
                                    tint = Palm.inkSoft, modifier = Modifier.size(14.dp),
                                )
                                if (repeats || e.hasFiles || sub.isNotEmpty()) Spacer(Modifier.width(4.dp))
                            }
                            if (repeats) {
                                Icon(
                                    Icons.Outlined.Autorenew, str("has_repeat"),
                                    tint = Palm.inkSoft, modifier = Modifier.size(14.dp),
                                )
                                if (e.hasFiles || sub.isNotEmpty()) Spacer(Modifier.width(4.dp))
                            }
                            if (e.hasFiles) {
                                Icon(
                                    Icons.Outlined.Description, str("has_documents"),
                                    tint = Palm.inkSoft, modifier = Modifier.size(14.dp),
                                )
                                if (sub.isNotEmpty()) Spacer(Modifier.width(4.dp))
                            }
                            if (sub.isNotEmpty()) {
                                Text(sub, style = Palm.small, color = Palm.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Значок итога на иконке события: галочка, крестик или стрелка переноса. */
@Composable
fun OutcomeBadge(o: Outcome, modifier: Modifier = Modifier) {
    val (bg, icon) = when (o.kind) {
        OutcomeKind.GOOD -> Color(0xFF2E7D32) to Icons.Filled.Check
        OutcomeKind.BAD -> Color(0xFFC0392B) to Icons.Filled.Close
        OutcomeKind.MOVED -> Palm.navy to Icons.AutoMirrored.Filled.Redo
    }
    Box(
        modifier.padding(1.dp).size(14.dp).clip(CircleShape).background(Palm.paper).padding(1.5.dp)
            .clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(9.dp)) }
}
