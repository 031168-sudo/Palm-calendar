package ru.palmdate.app.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import ru.palmdate.app.plu
import ru.palmdate.app.str
import ru.palmdate.app.time.lengthOfMonth
import ru.palmdate.app.time.now
import ru.palmdate.app.time.plusMonths
import ru.palmdate.app.time.value
import ru.palmdate.app.ui.shortMonth

/**
 * Свои правила повтора — как «Настроить…» в Google Календаре: каждые N дней/недель/месяцев/лет,
 * дни недели, день месяца или «вторая среда», окончание (никогда / дата / N раз).
 * Хранятся обычным RRULE — его понимают и Android, и Google.
 */
enum class RepFreq { DAILY, WEEKLY, MONTHLY, YEARLY }
enum class MonthMode { DAY, NTH }
enum class RepEnd { NEVER, DATE, COUNT }

data class RepeatSpec(
    val freq: RepFreq = RepFreq.WEEKLY,
    val interval: Int = 1,
    val days: Set<DayOfWeek> = emptySet(),          // для недель
    val monthMode: MonthMode = MonthMode.DAY,        // для месяцев: число месяца или «n-й день недели»
    val monthDay: Int = 1,
    val nth: Int = 1,                                // 1..5, -1 — последний
    val weekday: DayOfWeek = DayOfWeek.MONDAY,
    val end: RepEnd = RepEnd.NEVER,
    val until: LocalDate? = null,
    val count: Int = 10,
) {
    companion object {
        /** Какой по счёту такой день недели в месяце: 1..5. */
        fun nthOf(d: LocalDate) = (d.dayOfMonth - 1) / 7 + 1

        /** Последний ли это такой день недели в месяце. */
        fun isLastOfMonth(d: LocalDate) = d.dayOfMonth + 7 > d.lengthOfMonth()

        /** Заготовка для окна «Настроить…» — от даты события. */
        fun defaultFor(anchor: LocalDate) = RepeatSpec(
            freq = RepFreq.WEEKLY, days = setOf(anchor.dayOfWeek), monthDay = anchor.dayOfMonth,
            nth = nthOf(anchor), weekday = anchor.dayOfWeek, until = anchor.plusMonths(3),
        )

        private val CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
        fun code(d: DayOfWeek) = CODES[d.value - 1]
        private fun dayOf(code: String) = CODES.indexOf(code).takeIf { it >= 0 }?.let { DayOfWeek.entries[it] }

        private fun p2(n: Int) = n.toString().padStart(2, '0')
        private fun LocalDate.basic() = year.toString().padStart(4, '0') + p2(monthNumber) + p2(dayOfMonth)
        private fun parseDate(u: String) = LocalDate(u.substring(0, 4).toInt(), u.substring(4, 6).toInt(), u.substring(6, 8).toInt())

        /** RRULE → настройки; null — правило сложнее, чем умеем (тогда показываем как «своё»). */
        fun parse(rrule: String?, anchor: LocalDate, zone: TimeZone = TimeZone.currentSystemDefault()): RepeatSpec? {
            if (rrule.isNullOrBlank()) return null
            val p = rrule.uppercase().removePrefix("RRULE:").split(";").filter { it.isNotBlank() }
                .associate { it.substringBefore("=") to it.substringAfter("=") }
            if (p.keys.any { it !in setOf("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH", "COUNT", "UNTIL", "WKST") }) return null
            val freq = when (p["FREQ"]) {
                "DAILY" -> RepFreq.DAILY; "WEEKLY" -> RepFreq.WEEKLY; "MONTHLY" -> RepFreq.MONTHLY; "YEARLY" -> RepFreq.YEARLY
                else -> return null
            }
            val interval = (p["INTERVAL"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
            var spec = defaultFor(anchor).copy(freq = freq, interval = interval, days = emptySet())
            val byDay = p["BYDAY"]
            when (freq) {
                RepFreq.DAILY -> if (byDay != null || p["BYMONTHDAY"] != null) return null
                RepFreq.WEEKLY -> {
                    if (p["BYMONTHDAY"] != null) return null
                    val ds = byDay?.split(",")?.map { dayOf(it) ?: return null }?.toSet() ?: setOf(anchor.dayOfWeek)
                    spec = spec.copy(days = ds)
                }
                RepFreq.MONTHLY -> {
                    if (byDay != null && p["BYMONTHDAY"] != null) return null
                    if (byDay != null) {
                        val m = Regex("^(-?[1-5])(MO|TU|WE|TH|FR|SA|SU)$").matchEntire(byDay) ?: return null
                        val n = m.groupValues[1].toInt()
                        if (n != -1 && n < 1) return null
                        spec = spec.copy(monthMode = MonthMode.NTH, nth = n, weekday = dayOf(m.groupValues[2])!!)
                    } else {
                        val md = p["BYMONTHDAY"]?.toIntOrNull() ?: anchor.dayOfMonth
                        if (md < 1) return null
                        spec = spec.copy(monthMode = MonthMode.DAY, monthDay = md)
                    }
                }
                RepFreq.YEARLY -> {}
            }
            p["COUNT"]?.let { c -> spec = spec.copy(end = RepEnd.COUNT, count = c.toIntOrNull() ?: return null) }
            p["UNTIL"]?.let { u ->
                val d = runCatching {
                    if (u.contains("T")) {
                        val t = u.substringAfter("T").removeSuffix("Z")
                        LocalDateTime(parseDate(u).year, parseDate(u).monthNumber, parseDate(u).dayOfMonth, t.substring(0, 2).toInt(), t.substring(2, 4).toInt(), t.substring(4, 6).toInt())
                            .toInstant(TimeZone.UTC).toLocalDateTime(zone).date
                    } else parseDate(u)
                }.getOrNull() ?: return null
                spec = spec.copy(end = RepEnd.DATE, until = d)
            }
            return spec
        }
    }

    /** Настройки → RRULE. Самые простые правила записываются так же, как наши кнопки («FREQ=WEEKLY»). */
    fun toRrule(anchor: LocalDate, allDay: Boolean, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val parts = ArrayList<String>()
        parts += "FREQ=$freq"
        if (interval > 1) parts += "INTERVAL=$interval"
        val plain = interval == 1 && end == RepEnd.NEVER
        when (freq) {
            RepFreq.WEEKLY -> {
                val ds = days.ifEmpty { setOf(anchor.dayOfWeek) }
                if (!(plain && ds == setOf(anchor.dayOfWeek))) parts += "BYDAY=" + ds.sortedBy { it.value }.joinToString(",") { code(it) }
            }
            RepFreq.MONTHLY -> when (monthMode) {
                MonthMode.DAY -> if (!(plain && monthDay == anchor.dayOfMonth)) parts += "BYMONTHDAY=$monthDay"
                MonthMode.NTH -> parts += "BYDAY=$nth${code(weekday)}"
            }
            else -> {}
        }
        when (end) {
            RepEnd.COUNT -> parts += "COUNT=${count.coerceAtLeast(1)}"
            RepEnd.DATE -> {
                val d = until ?: anchor
                parts += "UNTIL=" + if (allDay) d.basic() else {
                    val u = LocalDateTime(d.year, d.monthNumber, d.dayOfMonth, 23, 59, 59).toInstant(zone).toLocalDateTime(TimeZone.UTC)
                    u.date.basic() + "T" + p2(u.hour) + p2(u.minute) + p2(u.second) + "Z"
                }
            }
            RepEnd.NEVER -> {}
        }
        return parts.joinToString(";")
    }

    /** Словами: «Каждые 2 недели по пн, ср, до 31 дек 2026». */
    fun describe(): String {
        val base = when (freq) {
            RepFreq.DAILY -> if (interval == 1) str("repeat_daily") else plu("rep_every_days", interval)
            RepFreq.WEEKLY -> {
                val w = if (interval == 1) str("repeat_weekly") else plu("rep_every_weeks", interval)
                if (days.isEmpty()) w
                else str("rep_on_days", w, days.sortedBy { it.value }.joinToString(", ") { L10n.array("wd_short")[it.value - 1] })
            }
            RepFreq.MONTHLY -> {
                val detail = monthDetail()
                when {
                    interval == 1 && monthMode == MonthMode.DAY -> detail
                    interval == 1 -> detail
                    else -> str("rep_join", plu("rep_every_months", interval), detail.replaceFirstChar { it.lowercase() })
                }
            }
            RepFreq.YEARLY -> if (interval == 1) str("repeat_yearly") else plu("rep_every_years", interval)
        }
        val tail = when (end) {
            RepEnd.NEVER -> null
            RepEnd.DATE -> str("rep_until", (until ?: LocalDate.now()).medium())
            RepEnd.COUNT -> plu("rep_times", count)
        }
        return if (tail == null) base else str("rep_join", base, tail)
    }

    /** «Каждое 14-е число» / «Каждую вторую среду». */
    fun monthDetail(): String = when (monthMode) {
        MonthMode.DAY -> str("rep_month_day", monthDay)
        MonthMode.NTH -> nthWeekdayLabel(nth, weekday)
    }
}

/** Дата с годом: «31 дек 2026» / «Dec 31, 2026». */
private fun LocalDate.medium(): String =
    if (L10n.language == "ru") "$dayOfMonth ${shortMonth()} $year" else "${shortMonth()} $dayOfMonth, $year"

/** «Каждую вторую среду» / «Every second Wednesday» (n = -1 — последняя). */
fun nthWeekdayLabel(n: Int, wd: DayOfWeek): String {
    val g = L10n.array("wd_gender")[wd.value - 1].toInt()
    val ords = L10n.array(when (g) { 0 -> "rep_ord_m"; 1 -> "rep_ord_f"; else -> "rep_ord_n" })
    val idx = if (n == -1) 5 else (n - 1).coerceIn(0, 4)
    return str("rep_month_nth", L10n.array("rep_each_g")[g], ords[idx], L10n.array("wd_acc")[wd.value - 1])
}
