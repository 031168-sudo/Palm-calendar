package ru.palmdate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.palmdate.app.Lang
import ru.palmdate.app.R
import ru.palmdate.app.model.MonthMode
import ru.palmdate.app.model.RepEnd
import ru.palmdate.app.model.RepFreq
import ru.palmdate.app.model.RepeatSpec
import ru.palmdate.app.model.nthWeekdayLabel
import ru.palmdate.app.str
import ru.palmdate.app.ui.theme.Palm
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * «Настроить повтор» — как в Google Календаре: каждые N дней/недель/месяцев/лет, дни недели
 * или «вторая среда», окончание (никогда / в дату / после N раз). Результат — обычный RRULE.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RepeatDialog(
    initial: RepeatSpec,
    anchor: LocalDate,
    allDay: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var spec by remember { mutableStateOf(initial) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palm.paper,
        title = { Text(str(R.string.rep_title), style = Palm.title, color = Palm.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Итог словами — так же, как покажется в карточке
                Text(spec.describe(), style = Palm.body, color = Palm.navy)
                Spacer(Modifier.height(12.dp))

                // Каждые N …
                Text(str(R.string.rep_every), style = Palm.small, color = Palm.inkSoft)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepIcon(Icons.Filled.Remove) { spec = spec.copy(interval = (spec.interval - 1).coerceAtLeast(1)) }
                    Text(spec.interval.toString(), style = Palm.title, color = Palm.navy, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                    StepIcon(Icons.Filled.Add) { spec = spec.copy(interval = (spec.interval + 1).coerceAtMost(99)) }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        RepFreq.DAILY to R.string.rep_unit_day, RepFreq.WEEKLY to R.string.rep_unit_week,
                        RepFreq.MONTHLY to R.string.rep_unit_month, RepFreq.YEARLY to R.string.rep_unit_year,
                    ).forEach { (f, label) -> Chip(str(label), selected = spec.freq == f) { spec = spec.copy(freq = f) } }
                }

                // Дни недели
                if (spec.freq == RepFreq.WEEKLY) {
                    Spacer(Modifier.height(12.dp))
                    val letters = Lang.context.resources.getStringArray(R.array.wd_letter)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { d ->
                            val on = d in spec.days
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (on) Palm.navy else Color.Transparent)
                                    .border(1.dp, if (on) Palm.navy else Palm.rule, CircleShape)
                                    .clickable {
                                        val next = if (on) spec.days - d else spec.days + d
                                        if (next.isNotEmpty()) spec = spec.copy(days = next)   // хотя бы один день
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(letters[d.value - 1], style = Palm.small, color = if (on) Color.White else Palm.ink) }
                        }
                    }
                }

                // Месяц: число или «n-й день недели»
                if (spec.freq == RepFreq.MONTHLY) {
                    Spacer(Modifier.height(12.dp))
                    val nthLabel = nthWeekdayLabel(RepeatSpec.nthOf(anchor).coerceAtMost(4), anchor.dayOfWeek)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(str(R.string.rep_month_day, anchor.dayOfMonth), selected = spec.monthMode == MonthMode.DAY && spec.monthDay == anchor.dayOfMonth) {
                            spec = spec.copy(monthMode = MonthMode.DAY, monthDay = anchor.dayOfMonth)
                        }
                        // «n-я среда» — только если это не пятая; пятая бывает не в каждом месяце
                        if (RepeatSpec.nthOf(anchor) <= 4) {
                            Chip(nthLabel, selected = spec.monthMode == MonthMode.NTH && spec.nth == RepeatSpec.nthOf(anchor) && spec.weekday == anchor.dayOfWeek) {
                                spec = spec.copy(monthMode = MonthMode.NTH, nth = RepeatSpec.nthOf(anchor), weekday = anchor.dayOfWeek)
                            }
                        }
                        if (RepeatSpec.isLastOfMonth(anchor)) {
                            Chip(nthWeekdayLabel(-1, anchor.dayOfWeek), selected = spec.monthMode == MonthMode.NTH && spec.nth == -1 && spec.weekday == anchor.dayOfWeek) {
                                spec = spec.copy(monthMode = MonthMode.NTH, nth = -1, weekday = anchor.dayOfWeek)
                            }
                        }
                        // Правило из Google, которого нет среди этих вариантов (например «каждое 5-е число»), — оставляем как есть
                        if (spec.monthMode == MonthMode.DAY && spec.monthDay != anchor.dayOfMonth) {
                            Chip(str(R.string.rep_month_day, spec.monthDay), selected = true) {}
                        }
                    }
                }

                // Окончание
                Spacer(Modifier.height(14.dp))
                Text(str(R.string.rep_ends), style = Palm.small, color = Palm.inkSoft)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(str(R.string.rep_end_never), selected = spec.end == RepEnd.NEVER) { spec = spec.copy(end = RepEnd.NEVER) }
                    Chip(str(R.string.rep_end_date), selected = spec.end == RepEnd.DATE) {
                        spec = spec.copy(end = RepEnd.DATE, until = spec.until ?: anchor.plusMonths(3))
                    }
                    Chip(str(R.string.rep_end_count), selected = spec.end == RepEnd.COUNT) { spec = spec.copy(end = RepEnd.COUNT) }
                }
                if (spec.end == RepEnd.DATE) {
                    Spacer(Modifier.height(8.dp))
                    Chip((spec.until ?: anchor).pretty()) { pickDate = true }
                }
                if (spec.end == RepEnd.COUNT) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepIcon(Icons.Filled.Remove) { spec = spec.copy(count = (spec.count - 1).coerceAtLeast(1)) }
                        Text(spec.count.toString(), style = Palm.title, color = Palm.navy, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                        StepIcon(Icons.Filled.Add) { spec = spec.copy(count = (spec.count + 1).coerceAtMost(999)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(spec.toRrule(anchor, allDay)) }) { Text(str(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (spec.until ?: anchor).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        spec = spec.copy(until = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickDate = false
                }) { Text(str(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(str(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}
