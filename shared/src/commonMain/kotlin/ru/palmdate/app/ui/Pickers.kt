package ru.palmdate.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import ru.palmdate.app.str
import ru.palmdate.app.time.*
import ru.palmdate.app.ui.theme.Palm

/** Стандартный календарь для выбора даты (тап по дате события). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.startOfDayUtcMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(utcMillisToDate(it)) }
                onDismiss()
            }) { Text(str("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str("cancel")) } },
    ) { DatePicker(state) }
}

/** Стандартный круглый выбор времени, 24 часа (тап по времени начала/конца). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palm.paper,
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime(state.hour, state.minute)); onDismiss() }) { Text(str("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str("cancel")) } },
    )
}

/** Нажимаемый текст (дата/время) — с лёгкой подложкой для области нажатия. */
fun Modifier.tappable(onClick: () -> Unit): Modifier =
    this.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp)

/** Новая длительность, если конец выбран по часам: конец на день начала, а если раньше начала — на следующий день. */
fun minutesForEnd(start: LocalDateTime, endTime: LocalTime, fallbackEndDate: LocalDate): Int {
    var end = fallbackEndDate.atTime(endTime)
    if (!end.isAfter(start)) end = start.toLocalDate().atTime(endTime)
    if (!end.isAfter(start)) end = end.plusDays(1)
    return minutesBetween(start, end).toInt().coerceAtLeast(5)
}
