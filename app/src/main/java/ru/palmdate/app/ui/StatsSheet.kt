package ru.palmdate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.ContactStat
import ru.palmdate.app.model.EventType
import ru.palmdate.app.ui.theme.Palm
import java.time.LocalDate

private enum class StatPeriod(val label: String, val from: () -> LocalDate) {
    MONTH("30 дней", { LocalDate.now().minusDays(30) }),
    YEAR("Год", { LocalDate.now().minusYears(1) }),
    ALL("Всё", { LocalDate.now().minusYears(10) }),
}

private enum class StatSort { TOTAL, CALLS, MEETINGS }

/** Что сделать с человеком из статистики. */
sealed interface StatAction {
    data class CallNow(val contact: ContactRef) : StatAction
    data class Create(val type: EventType, val contact: ContactRef) : StatAction
    data class History(val contact: ContactRef) : StatAction
}

/**
 * Статистика по всем людям: сколько состоялось звонков и встреч.
 * Сортировка — по заголовкам столбцов, тап по человеку — меню действий.
 */
@Composable
fun StatsSheet(
    load: suspend (LocalDate) -> List<ContactStat>,
    onAction: (StatAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var period by remember { mutableStateOf(StatPeriod.YEAR) }
    var sort by remember { mutableStateOf(StatSort.TOTAL) }
    var rows by remember { mutableStateOf<List<ContactStat>?>(null) }
    LaunchedEffect(period) {
        rows = null
        rows = load(period.from())
    }

    PalmSheet(onDismissRequest = onDismiss, fixedHeight = false) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Статистика", style = Palm.title, color = Palm.navy, modifier = Modifier.weight(1f))
                // Период
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatPeriod.entries.forEach { p ->
                        val sel = p == period
                        Text(
                            p.label,
                            style = Palm.small.copy(fontWeight = FontWeight.Bold),
                            color = if (sel) Color.White else Palm.navy,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) Palm.navy else Color.Transparent)
                                .clickable { period = p }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Text(
                "Состоявшиеся звонки и встречи (с итогом «Состоялось»)",
                style = Palm.small, color = Palm.inkSoft,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(8.dp))

            // Заголовки столбцов — по ним сортировка
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Человек", style = HeaderStyle, color = Palm.inkSoft, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
                Header("Звонки", sort == StatSort.CALLS, Modifier.width(COL_W)) { sort = StatSort.CALLS }
                Header("Встречи", sort == StatSort.MEETINGS, Modifier.width(COL_W)) { sort = StatSort.MEETINGS }
                Header("Всего", sort == StatSort.TOTAL, Modifier.width(COL_W)) { sort = StatSort.TOTAL }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))
        }

        val list = rows
        if (list == null) {
            Text("Считаю…", style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(16.dp))
            return@PalmSheet
        }
        if (list.isEmpty()) {
            Text(
                "За этот период нет состоявшихся звонков и встреч. Считаются события, " +
                    "привязанные к человеку, у которых отмечен итог «Состоялось».",
                style = Palm.body, color = Palm.inkSoft, modifier = Modifier.padding(16.dp),
            )
            return@PalmSheet
        }

        val sorted = when (sort) {
            StatSort.TOTAL -> list.sortedWith(compareByDescending<ContactStat> { it.total }.thenBy { it.contact.name })
            StatSort.CALLS -> list.sortedWith(compareByDescending<ContactStat> { it.calls }.thenByDescending { it.total }.thenBy { it.contact.name })
            StatSort.MEETINGS -> list.sortedWith(compareByDescending<ContactStat> { it.meetings }.thenByDescending { it.total }.thenBy { it.contact.name })
        }

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(bottom = 16.dp)) {
            items(sorted, key = { it.contact.lookupKey }) { s -> StatRow(s, sort, onAction) }
            item {
                Text(
                    "${plural(sorted.size, "человек", "человека", "человек")} · " +
                        "${plural(sorted.sumOf { it.calls }, "звонок", "звонка", "звонков")} · " +
                        plural(sorted.sumOf { it.meetings }, "встреча", "встречи", "встреч"),
                    style = Palm.small, color = Palm.inkSoft,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Ширина столбцов с числами. */
private val COL_W = 64.dp
private val HeaderStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
private val NumStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)

/** Заголовок столбца: нажатие — сортировать по нему; у активного — стрелка. */
@Composable
private fun Header(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        if (active) "$text ▾" else text,
        style = HeaderStyle,
        color = if (active) Palm.navy else Palm.inkSoft,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}

@Composable
private fun StatRow(s: ContactStat, sort: StatSort, onAction: (StatAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { menu = true }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                s.contact.name, style = Palm.body, color = Palm.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Num(s.calls, sort == StatSort.CALLS)
            Num(s.meetings, sort == StatSort.MEETINGS)
            Num(s.total, sort == StatSort.TOTAL)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Palm.rule).align(Alignment.BottomCenter))

        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val c = s.contact
            MenuItem("Позвонить сейчас", Icons.Outlined.Phone) { menu = false; onAction(StatAction.CallNow(c)) }
            MenuItem("Создать звонок", EventType.CALL.icon) { menu = false; onAction(StatAction.Create(EventType.CALL, c)) }
            MenuItem("Создать встречу", EventType.MEETING.icon) { menu = false; onAction(StatAction.Create(EventType.MEETING, c)) }
            MenuItem("История", Icons.Outlined.History) { menu = false; onAction(StatAction.History(c)) }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = Palm.body) },
        leadingIcon = { Icon(icon, null, tint = Palm.navy, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun Num(n: Int, bold: Boolean) {
    Text(
        if (n == 0) "–" else n.toString(),
        style = if (bold) NumStyle.copy(fontWeight = FontWeight.Bold) else NumStyle,
        color = if (n == 0) Palm.inkSoft else Palm.ink,
        textAlign = TextAlign.End,
        modifier = Modifier.width(COL_W).padding(end = 10.dp),
    )
}
