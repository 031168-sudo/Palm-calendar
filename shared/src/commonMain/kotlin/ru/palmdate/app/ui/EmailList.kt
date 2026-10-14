package ru.palmdate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.palmdate.app.model.EmailContact
import ru.palmdate.app.str
import ru.palmdate.app.ui.theme.Palm

/** Список "имя — адрес" (для «Кому» и для шага «Написать»). */
@Composable
fun EmailList(list: List<EmailContact>, modifier: Modifier, onPick: (EmailContact) -> Unit) {
    if (list.isEmpty()) {
        Text(str("mailui_no_people"), style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    LazyColumn(modifier.fillMaxWidth()) {
        items(list, key = { it.email }) { p ->
            Column(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 7.dp)) {
                Text(p.name, style = Palm.body, color = Palm.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.email, style = Palm.small, color = Palm.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.rule))
        }
    }
}


