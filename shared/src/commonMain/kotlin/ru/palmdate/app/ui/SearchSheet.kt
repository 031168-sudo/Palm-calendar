package ru.palmdate.app.ui

import ru.palmdate.app.str
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.ui.theme.Palm
import ru.palmdate.app.time.*

/** Строки списка найденного: заголовок года или событие (дата слева — только у первого события дня). */
private sealed interface SRow {
    data class Year(val year: Int) : SRow
    data class Item(val e: PalmEvent, val showDate: Boolean) : SRow
}

/** Подходит ли событие под поиск: название, заметка, имя контакта, адрес, итог — без учёта регистра. */
private fun PalmEvent.matches(q: String): Boolean =
    listOfNotNull(title, note, contact?.name, place, outcomeNote).any { it.lowercase().contains(q) }

/**
 * Поиск событий (лупа в нижней панели): поле сверху, ниже — найденное по датам, как в Google Календаре:
 * слева месяц и число, справа строка события (начало и конец). Список открывается на сегодняшнем дне,
 * прошлые события — выше. [load] — все события за период поиска (2 года назад и 2 года вперёд).
 */
@Composable
fun SearchSheet(
    load: suspend () -> List<PalmEvent>,
    onEvent: (PalmEvent) -> Unit,
    onDismiss: () -> Unit,
    initialQuery: String = "", // для скриншотов в тестах
) {
    var all by remember { mutableStateOf<List<PalmEvent>?>(null) }
    LaunchedEffect(Unit) { all = load() }
    var query by remember { mutableStateOf(initialQuery) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(250); runCatching { focus.requestFocus() } }

    PalmSheet(onDismissRequest = onDismiss, fixedHeight = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str("search_title"), style = Palm.title, color = Palm.navy, modifier = Modifier.weight(1f))
                SheetCloseButton(onDismiss)
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(str("search_hint")) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Palm.navy) },
                trailingIcon = if (query.isEmpty()) null else ({
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, str("search_clear"), tint = Palm.inkSoft) }
                }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))
        }

        val list = all
        val q = query.trim().lowercase()
        when {
            list == null -> Hint(str("loading"))
            q.isEmpty() -> Hint(str("search_prompt"))
            else -> {
                val found = remember(list, q) { list.filter { it.matches(q) }.sortedBy { it.start } }
                if (found.isEmpty()) Hint(str("search_nothing")) else key(q) { Results(found, onEvent) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text, style = Palm.body, color = Palm.inkSoft, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Results(found: List<PalmEvent>, onEvent: (PalmEvent) -> Unit) {
    val today = LocalDate.now()
    val rows = remember(found) {
        buildList {
            var lastYear = today.year
            var lastDay: LocalDate? = null
            found.forEach { e ->
                val d = e.start.toLocalDate()
                if (d.year != lastYear) { add(SRow.Year(d.year)); lastYear = d.year }
                add(SRow.Item(e, showDate = d != lastDay))
                lastDay = d
            }
        }
    }
    // Открываем на сегодняшнем (или ближайшем следующем) событии; прошлые — выше
    val start = rows.indexOfFirst { it is SRow.Item && it.e.start.toLocalDate() >= today }.let { if (it < 0) rows.lastIndex else it }
        .let { i -> if (i > 0 && rows[i - 1] is SRow.Year) i - 1 else i }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
        items(rows) { r ->
            when (r) {
                is SRow.Year -> Text(
                    "${r.year}", style = Palm.title, color = Palm.navy,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                )
                is SRow.Item -> {
                    val d = r.e.start.toLocalDate()
                    val color = if (d == today) Palm.nowLine else Palm.inkSoft
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (r.showDate) {
                                Text(d.shortMonth(), style = Palm.small, color = color, maxLines = 1)
                                Text("${d.dayOfMonth}", style = Palm.title, color = if (d == today) Palm.nowLine else Palm.ink, maxLines = 1)
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            EventLine(
                                r.e, if (r.e.allDay) "" else r.e.start.format(HM), highlight = d == today,
                                onIcon = { onEvent(it) }, onEvent = onEvent,
                            )
                        }
                    }
                }
            }
        }
    }
}
