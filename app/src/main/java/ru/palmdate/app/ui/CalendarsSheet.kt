package ru.palmdate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.ui.theme.Palm

/**
 * Какие календари показывать — галочки, как в Google Календаре.
 * Заодно видно состояние каждого: только чтение, синхронизация выключена, только на телефоне.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarsSheet(
    load: suspend () -> List<CalendarInfo>,
    hidden: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var calendars by remember { mutableStateOf<List<CalendarInfo>?>(null) }
    LaunchedEffect(Unit) { calendars = load() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palm.paper,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Layers, null, tint = Palm.navy, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Календари", style = Palm.title, color = Palm.ink)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))

            val list = calendars
            if (list == null) {
                Text("Загрузка…", style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(top = 12.dp))
                return@Column
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                list.groupBy { it.accountName }.forEach { (account, cals) ->
                    item(key = "acc:$account") {
                        Text(
                            account, style = Palm.button, color = Palm.navy,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                        )
                    }
                    items(cals, key = { it.id }) { c ->
                        val shown = c.id !in hidden
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onToggle(c.id, !shown) }.dottedRule(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = shown,
                                onCheckedChange = { onToggle(c.id, it) },
                                colors = CheckboxDefaults.colors(checkedColor = Color(c.color), uncheckedColor = Color(c.color)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(c.displayName(), style = Palm.body, color = Palm.ink)
                                c.problem?.let { Text(it, style = Palm.small, color = Palm.inkSoft) }
                            }
                        }
                    }
                }
                item(key = "hint") {
                    Text(
                        buildString {
                            append("Дни рождения берутся из карточек контактов. ")
                            if (list.any { !it.synced }) {
                                append("Если у календаря выключена синхронизация, его событий на телефоне нет: включите её в Google Календаре → Настройки → календарь → «Синхронизация». ")
                            }
                            append("Задачи Google сторонним приложениям недоступны.")
                        },
                        style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
