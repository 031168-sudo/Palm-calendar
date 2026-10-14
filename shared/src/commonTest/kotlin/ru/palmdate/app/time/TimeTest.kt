package ru.palmdate.app.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Слой дат должен вести себя так же, как java.time, на котором приложение работало раньше. */
class TimeTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)
    private fun dt(y: Int, m: Int, day: Int, h: Int, min: Int) = LocalDateTime(y, m, day, h, min)

    @Test fun dateArithmetic() {
        assertEquals(d(2026, 11, 1), d(2026, 10, 31).plusDays(1))
        assertEquals(d(2026, 10, 9), d(2026, 10, 10).minusDays(1))
        assertEquals(d(2026, 10, 21), d(2026, 10, 14).plusWeeks(1))
        // конец месяца: 31 января + месяц = 28 февраля, как в java.time
        assertEquals(d(2026, 2, 28), d(2026, 1, 31).plusMonths(1))
        assertEquals(d(2024, 2, 29), d(2024, 1, 31).plusMonths(1))
        assertEquals(d(2029, 2, 28), d(2028, 2, 29).plusYears(1))
        assertEquals(d(2025, 10, 14), d(2026, 10, 14).minusYears(1))
        assertEquals(d(2026, 10, 1), d(2026, 10, 14).withDayOfMonth(1))
        assertEquals(d(2026, 1, 1), d(2026, 10, 14).withDayOfYear(1))
        assertEquals(d(2026, 2, 1), d(2026, 1, 1).plusDays(31))
    }

    @Test fun monthLength() {
        assertEquals(31, d(2026, 10, 14).lengthOfMonth())
        assertEquals(28, d(2026, 2, 3).lengthOfMonth())
        assertEquals(29, d(2024, 2, 3).lengthOfMonth())
        assertEquals(30, d(2026, 11, 30).lengthOfMonth())
    }

    @Test fun weekStart() {
        // 14 октября 2026 — среда
        assertEquals(DayOfWeek.WEDNESDAY, d(2026, 10, 14).dayOfWeek)
        assertEquals(d(2026, 10, 12), d(2026, 10, 14).previousOrSame(DayOfWeek.MONDAY))
        assertEquals(d(2026, 10, 11), d(2026, 10, 14).previousOrSame(DayOfWeek.SUNDAY))
        assertEquals(d(2026, 10, 12), d(2026, 10, 12).previousOrSame(DayOfWeek.MONDAY))
        assertEquals(d(2026, 10, 5), d(2026, 10, 11).previousOrSame(DayOfWeek.MONDAY))
        assertEquals(3, d(2026, 10, 14).dayOfWeek.value)
        assertEquals(7, d(2026, 10, 11).dayOfWeek.value)
        assertEquals(1, d(2026, 10, 12).dayOfWeek.value)
    }

    @Test fun compare() {
        assertTrue(d(2026, 10, 14).isAfter(d(2026, 10, 13)))
        assertFalse(d(2026, 10, 14).isAfter(d(2026, 10, 14)))
        assertTrue(d(2026, 10, 14).isBefore(d(2026, 10, 15)))
        assertTrue(dt(2026, 10, 14, 9, 1).isAfter(dt(2026, 10, 14, 9, 0)))
        assertTrue(dt(2026, 10, 14, 9, 0).isBefore(dt(2026, 10, 14, 9, 1)))
    }

    @Test fun dateTimeArithmetic() {
        assertEquals(dt(2026, 10, 15, 0, 15), dt(2026, 10, 14, 23, 45).plusMinutes(30))
        assertEquals(dt(2026, 10, 14, 23, 45), dt(2026, 10, 15, 0, 15).minusMinutes(30))
        assertEquals(dt(2026, 10, 14, 10, 30), dt(2026, 10, 14, 9, 30).plusHours(1))
        assertEquals(dt(2026, 11, 1, 9, 30), dt(2026, 10, 31, 9, 30).plusDays(1))
        assertEquals(dt(2026, 10, 13, 9, 30), dt(2026, 10, 14, 9, 30).minusDays(1))
        assertEquals(dt(2026, 10, 21, 9, 30), dt(2026, 10, 14, 9, 30).plusWeeks(1))
        assertEquals(dt(2026, 10, 14, 9, 30), dt(2026, 10, 14, 0, 0).withHour(9).withMinute(30))
        assertEquals(d(2026, 10, 14), dt(2026, 10, 14, 23, 59).toLocalDate())
        assertEquals(dt(2026, 10, 14, 0, 0), d(2026, 10, 14).atStartOfDay())
        assertEquals(dt(2026, 10, 14, 10, 0), d(2026, 10, 14).atTime(10, 0))
    }

    @Test fun lastNanoOfDay() {
        // конец события в полночь — это ещё предыдущий день (так считает PalmEvent.days)
        val end = dt(2026, 10, 15, 0, 0)
        assertEquals(d(2026, 10, 14), end.minusNanos(1).toLocalDate())
    }

    @Test fun minutes() {
        assertEquals(90L, minutesBetween(dt(2026, 10, 14, 9, 0), dt(2026, 10, 14, 10, 30)))
        assertEquals(1440L, minutesBetween(dt(2026, 10, 14, 0, 0), dt(2026, 10, 15, 0, 0)))
        assertEquals(-5L, minutesBetween(dt(2026, 10, 14, 9, 5), dt(2026, 10, 14, 9, 0)))
    }

    @Test fun hourMinuteFormat() {
        assertEquals("9:05", dt(2026, 10, 14, 9, 5).format(HM))
        assertEquals("23:59", dt(2026, 10, 14, 23, 59).format(HM))
        assertEquals("0:00", LocalTime(0, 0).format(HM))
        assertEquals("14:00", LocalTime.of(14, 0).format(HM))
    }

    @Test fun utcMillis() {
        // 2026-10-10T00:00:00Z
        val ms = 1_791_590_400_000L
        assertEquals(d(2026, 10, 10), utcMillisToDate(ms))
        assertEquals(ms, d(2026, 10, 10).startOfDayUtcMillis())
        assertEquals(d(2026, 10, 9), utcMillisToDate(ms - 1))
    }

    @Test fun nowIsSane() {
        assertTrue(LocalDate.now().year >= 2026)
        assertTrue(LocalDateTime.now().year >= 2026)
        assertTrue(LocalTime.now().hour in 0..23)
    }
}
