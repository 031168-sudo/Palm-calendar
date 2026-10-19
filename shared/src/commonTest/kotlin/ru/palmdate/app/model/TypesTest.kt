package ru.palmdate.app.model

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Тексты в тестах — сами ключи: так видно, какая строка выбрана, и не нужен Android. */
private object FakeStrings : StringProvider {
    override fun str(key: String, vararg args: Any): String =
        if (args.isEmpty()) key else key + args.joinToString(",", "(", ")")

    override fun strIn(tag: String, key: String): String = "$tag:$key"
    override val tags: List<String> = listOf("ru", "en")
    override val language: String = "ru"
    override fun plural(key: String, n: Int): String = "$key[$n]"
    override fun array(key: String): List<String> = emptyList()
}

class TypesTest {
    @BeforeTest fun setUp() { L10n.provider = FakeStrings }

    @Test fun eventTypeParse() {
        assertEquals(EventType.CALL, EventType.parse("CALL"))
        assertNull(EventType.parse("BIRTHDAY"))
        assertNull(EventType.parse(null))
        assertFalse(EventType.BIRTHDAY in EventType.pickable)
        assertEquals("type_call", EventType.CALL.label)
        assertEquals(listOf("ru:type_call", "en:type_call"), EventType.CALL.allLabels)
    }

    @Test fun outcomeOptionsAndLabels() {
        assertEquals(listOf(Outcome.DONE, Outcome.NOT_DONE), Outcome.optionsFor(EventType.TASK))
        assertEquals("outcome_done_call", Outcome.DONE.label(EventType.CALL))
        assertEquals("outcome_not_sent", Outcome.NOT_DONE.label(EventType.MAIL))
        assertEquals("outcome_cancelled_f", Outcome.CANCELLED.label(EventType.TASK))
        assertEquals(OutcomeKind.MOVED, Outcome.RESCHEDULED.kind)
    }

    @Test fun outcomeFromLabelAnyLanguage() {
        assertEquals(Outcome.DONE, Outcome.fromLabel("ru:outcome_done_call"))
        assertEquals(Outcome.DONE, Outcome.fromLabel("  EN:Outcome_Done_Meeting "))
        assertEquals(Outcome.NO_ANSWER, Outcome.fromLabel("en:outcome_no_answer"))
        assertNull(Outcome.fromLabel("что-то другое"))
    }

    @Test fun reminderLabels() {
        assertEquals("rem_at_start", reminderLabel(0))
        assertEquals("rem_hour(1)", reminderLabel(60))
        assertEquals("rem_one_day", reminderLabel(1440))
        assertEquals("rem_days(2)", reminderLabel(2880))
        assertEquals("rem_hour(2)", reminderLabel(120))
        assertEquals("rem_min(7)", reminderLabel(7))
        assertEquals("rem_allday_same(8:00)", allDayReminderLabel(-480))
        assertEquals("rem_allday_before(18:00)", allDayReminderLabel(360))
    }

    @Test fun repeatLabels() {
        assertEquals("repeat_none", repeatLabel(null))
        assertEquals("repeat_monthly", repeatLabel("FREQ=MONTHLY"))
        assertEquals("repeat_custom", repeatLabel("FREQ=DAILY;INTERVAL=2"))
        assertEquals("mail_verb_reply", MailKind.REPLY.verb)
        assertEquals(listOf("ru:mail_verb_new", "en:mail_verb_new"), MailKind.NEW.allVerbs)
    }
}
