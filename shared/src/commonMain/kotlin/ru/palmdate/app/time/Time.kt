package ru.palmdate.app.time

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

// Даты для Android и iOS: kotlinx-datetime с привычными именами методов (как в java.time),
// чтобы код экранов и логики не пришлось переписывать. Арифметика над LocalDateTime — по «настенному»
// времени (как в java.time.LocalDateTime), без часовых поясов.

private val UTC = TimeZone.UTC

// ---------- «сейчас» и конструкторы ----------

fun LocalDate.Companion.now(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
fun LocalDate.Companion.of(year: Int, month: Int, day: Int): LocalDate = LocalDate(year, month, day)
fun LocalDateTime.Companion.now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
fun LocalTime.Companion.now(): LocalTime = LocalDateTime.now().time
fun LocalTime.Companion.of(hour: Int, minute: Int): LocalTime = LocalTime(hour, minute)

/** Номер дня недели, как в java.time: понедельник — 1, воскресенье — 7. */
val DayOfWeek.value: Int get() = ordinal + 1

// ---------- LocalDate ----------

fun LocalDate.plusDays(n: Long): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.plusDays(n: Int): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Long): LocalDate = minus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Int): LocalDate = minus(n, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(n: Long): LocalDate = plus(n * 7, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(n: Int): LocalDate = plus(n * 7, DateTimeUnit.DAY)
fun LocalDate.plusMonths(n: Long): LocalDate = plus(n, DateTimeUnit.MONTH)
fun LocalDate.plusMonths(n: Int): LocalDate = plus(n, DateTimeUnit.MONTH)
fun LocalDate.plusYears(n: Long): LocalDate = plus(n, DateTimeUnit.YEAR)
fun LocalDate.plusYears(n: Int): LocalDate = plus(n, DateTimeUnit.YEAR)
fun LocalDate.minusYears(n: Long): LocalDate = minus(n, DateTimeUnit.YEAR)
fun LocalDate.minusYears(n: Int): LocalDate = minus(n, DateTimeUnit.YEAR)

fun LocalDate.isAfter(other: LocalDate): Boolean = this > other
fun LocalDate.isBefore(other: LocalDate): Boolean = this < other

fun LocalDate.withDayOfMonth(day: Int): LocalDate = LocalDate(year, monthNumber, day)
fun LocalDate.withDayOfYear(n: Int): LocalDate = LocalDate(year, 1, 1).plusDays(n - 1)
fun LocalDate.lengthOfMonth(): Int =
    LocalDate(year, monthNumber, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth

fun LocalDate.atStartOfDay(): LocalDateTime = LocalDateTime(year, monthNumber, dayOfMonth, 0, 0)
fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime = LocalDateTime(year, monthNumber, dayOfMonth, hour, minute)
fun LocalDate.atTime(t: LocalTime): LocalDateTime = LocalDateTime(year, monthNumber, dayOfMonth, t.hour, t.minute)

/** Ближайший [first] не позже этой даты (начало недели). */
fun LocalDate.previousOrSame(first: DayOfWeek): LocalDate =
    minus((dayOfWeek.ordinal - first.ordinal + 7) % 7, DateTimeUnit.DAY)

/** Полночь UTC этой даты, в миллисекундах (так хранятся события «на весь день»). */
fun LocalDate.startOfDayUtcMillis(): Long = atStartOfDayIn(UTC).toEpochMilliseconds()

/** Дата по миллисекундам UTC. */
fun utcMillisToDate(ms: Long): LocalDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(UTC).date

// ---------- LocalDateTime ----------

fun LocalDateTime.toLocalDate(): LocalDate = date
fun LocalDateTime.toLocalTime(): LocalTime = time

fun LocalDateTime.isAfter(other: LocalDateTime): Boolean = this > other
fun LocalDateTime.isBefore(other: LocalDateTime): Boolean = this < other

private fun LocalDateTime.shift(d: Duration): LocalDateTime = (toInstant(UTC) + d).toLocalDateTime(UTC)

private fun LocalDateTime.onDate(d: LocalDate): LocalDateTime =
    LocalDateTime(d.year, d.monthNumber, d.dayOfMonth, hour, minute, second, nanosecond)

fun LocalDateTime.plusMinutes(n: Long): LocalDateTime = shift(n.minutes)
fun LocalDateTime.plusMinutes(n: Int): LocalDateTime = shift(n.minutes)
fun LocalDateTime.minusMinutes(n: Long): LocalDateTime = shift((-n).minutes)
fun LocalDateTime.minusMinutes(n: Int): LocalDateTime = shift((-n).minutes)
fun LocalDateTime.plusHours(n: Long): LocalDateTime = shift(n.hours)
fun LocalDateTime.plusHours(n: Int): LocalDateTime = shift(n.hours)
fun LocalDateTime.minusNanos(n: Long): LocalDateTime = shift((-n).nanoseconds)

fun LocalDateTime.plusDays(n: Long): LocalDateTime = onDate(date.plusDays(n))
fun LocalDateTime.plusDays(n: Int): LocalDateTime = onDate(date.plusDays(n))
fun LocalDateTime.minusDays(n: Long): LocalDateTime = onDate(date.minusDays(n))
fun LocalDateTime.minusDays(n: Int): LocalDateTime = onDate(date.minusDays(n))
fun LocalDateTime.plusWeeks(n: Long): LocalDateTime = onDate(date.plusWeeks(n))
fun LocalDateTime.plusWeeks(n: Int): LocalDateTime = onDate(date.plusWeeks(n))

fun LocalDateTime.withHour(h: Int): LocalDateTime = LocalDateTime(year, monthNumber, dayOfMonth, h, minute, second, nanosecond)
fun LocalDateTime.withMinute(m: Int): LocalDateTime = LocalDateTime(year, monthNumber, dayOfMonth, hour, m, second, nanosecond)
fun LocalDateTime.withSecond(s: Int): LocalDateTime = LocalDateTime(year, monthNumber, dayOfMonth, hour, minute, s, nanosecond)
fun LocalDateTime.withNano(n: Int): LocalDateTime = LocalDateTime(year, monthNumber, dayOfMonth, hour, minute, second, n)

/** Минут от [a] до [b] (целых, в сторону нуля), по «настенному» времени. */
fun minutesBetween(a: LocalDateTime, b: LocalDateTime): Long = (b.toInstant(UTC) - a.toInstant(UTC)).inWholeMinutes

// ---------- Время «Ч:ММ» ----------

/** Формат времени «9:05» (час без нуля, минуты с нулём): `time.format(HM)`. */
object HM

@Suppress("UNUSED_PARAMETER")
fun LocalDateTime.format(f: HM): String = "$hour:${minute.toString().padStart(2, '0')}"

@Suppress("UNUSED_PARAMETER")
fun LocalTime.format(f: HM): String = "$hour:${minute.toString().padStart(2, '0')}"
