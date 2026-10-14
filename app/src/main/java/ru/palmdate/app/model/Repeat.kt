package ru.palmdate.app.model

import ru.palmdate.app.Lang
import ru.palmdate.app.R
import ru.palmdate.app.plu
import ru.palmdate.app.str
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
        private fun dayOf(code: String) = CODES.indexOf(code).takeIf { it >= 0 }?.let { DayOfWeek.of(it + 1) }

        private val UNTIL_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

        /** RRULE → настройки; null — правило сложнее, чем умеем (тогда показываем как «своё»). */
        fun parse(rrule: String?, anchor: LocalDate, zone: ZoneId = ZoneId.systemDefault()): RepeatSpec? {
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
                    if (u.contains("T")) LocalDateTime.parse(u.removeSuffix("Z"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                        .atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate()
                    else LocalDate.parse(u.take(8), DateTimeFormatter.BASIC_ISO_DATE)
                }.getOrNull() ?: return null
                spec = spec.copy(end = RepEnd.DATE, until = d)
            }
            return spec
        }
    }

    /** Настройки → RRULE. Самые простые правила записываются так же, как наши кнопки («FREQ=WEEKLY»). */
    fun toRrule(anchor: LocalDate, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
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
                parts += "UNTIL=" + if (allDay) d.format(DateTimeFormatter.BASIC_ISO_DATE)
                else UNTIL_UTC.format(d.atTime(23, 59, 59).atZone(zone).withZoneSameInstant(ZoneOffset.UTC))
            }
            RepEnd.NEVER -> {}
        }
        return parts.joinToString(";")
    }

    /** Словами: «Каждые 2 недели по пн, ср, до 31 дек 2026». */
    fun describe(): String {
        val res = Lang.context.resources
        val base = when (freq) {
            RepFreq.DAILY -> if (interval == 1) str(R.string.repeat_daily) else plu(R.plurals.rep_every_days, interval)
            RepFreq.WEEKLY -> {
                val w = if (interval == 1) str(R.string.repeat_weekly) else plu(R.plurals.rep_every_weeks, interval)
                if (days.isEmpty()) w
                else str(R.string.rep_on_days, w, days.sortedBy { it.value }.joinToString(", ") { res.getStringArray(R.array.wd_short)[it.value - 1] })
            }
            RepFreq.MONTHLY -> {
                val detail = monthDetail()
                when {
                    interval == 1 && monthMode == MonthMode.DAY -> detail
                    interval == 1 -> detail
                    else -> str(R.string.rep_join, plu(R.plurals.rep_every_months, interval), detail.replaceFirstChar { it.lowercase() })
                }
            }
            RepFreq.YEARLY -> if (interval == 1) str(R.string.repeat_yearly) else plu(R.plurals.rep_every_years, interval)
        }
        val tail = when (end) {
            RepEnd.NEVER -> null
            RepEnd.DATE -> str(R.string.rep_until, (until ?: LocalDate.now()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Lang.locale)))
            RepEnd.COUNT -> plu(R.plurals.rep_times, count)
        }
        return if (tail == null) base else str(R.string.rep_join, base, tail)
    }

    /** «Каждое 14-е число» / «Каждую вторую среду». */
    fun monthDetail(): String = when (monthMode) {
        MonthMode.DAY -> str(R.string.rep_month_day, monthDay)
        MonthMode.NTH -> nthWeekdayLabel(nth, weekday)
    }
}

/** «Каждую вторую среду» / «Every second Wednesday» (n = -1 — последняя). */
fun nthWeekdayLabel(n: Int, wd: DayOfWeek): String {
    val res = Lang.context.resources
    val g = res.getIntArray(R.array.wd_gender)[wd.value - 1]
    val ords = res.getStringArray(when (g) { 0 -> R.array.rep_ord_m; 1 -> R.array.rep_ord_f; else -> R.array.rep_ord_n })
    val idx = if (n == -1) 5 else (n - 1).coerceIn(0, 4)
    return str(R.string.rep_month_nth, res.getStringArray(R.array.rep_each_g)[g], ords[idx], res.getStringArray(R.array.wd_acc)[wd.value - 1])
}
