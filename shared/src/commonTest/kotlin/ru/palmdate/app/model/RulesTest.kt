package ru.palmdate.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Тесты общей логики: идут и на Android, и (позже) на iOS. */
class RulesTest {

    @Test fun repeatRules() {
        assertNull(matchRepeat(null))
        assertNull(matchRepeat("  "))
        assertEquals("FREQ=DAILY", matchRepeat("FREQ=DAILY"))
        assertEquals("FREQ=WEEKLY", matchRepeat("FREQ=WEEKLY;BYDAY=TU;WKST=MO"))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", matchRepeat("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"))
        assertEquals("", matchRepeat("FREQ=DAILY;COUNT=5"))
        assertEquals("", matchRepeat("FREQ=DAILY;INTERVAL=2"))
        assertEquals("FREQ=MONTHLY", matchRepeat("FREQ=MONTHLY"))
        assertEquals("FREQ=YEARLY", matchRepeat("freq=yearly"))
    }

    @Test fun minutesToTimeFormat() {
        assertEquals("9:00", minutesToTime(540))
        assertEquals("0:05", minutesToTime(5))
        assertEquals("23:59", minutesToTime(23 * 60 + 59))
        assertEquals("24:00", minutesToTime(1440))
    }

    @Test fun statTotal() {
        val c = ContactRef("k", "Имя")
        assertEquals(6, ContactStat(c, calls = 1, meetings = 2, mails = 3).total)
    }
}
