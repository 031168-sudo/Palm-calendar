package ru.palmdate.app.model

import ru.palmdate.app.time.now

// Типы, итоги, повторы и напоминания: общий код для Android и iOS.
// Тексты берутся по ключу через L10n (на Android — из strings.xml), значки и цвета типов — в приложении.

private fun str(key: String, vararg args: Any): String = L10n.str(key, *args)

/**
 * Тип события — то, что делало Agendus на Palm удобным:
 * не "текст во времени", а "звонок Сергею" / "встреча с Олегом".
 */
enum class EventType(
    val labelKey: String,
    /** Базовый цвет типа, ARGB (в приложении поправляется под тему). */
    val baseColorArgb: Long,
    val defaultMinutes: Int,
    val needsContact: Boolean,
    val defaultReminders: List<Int>,
) {
    CALL("type_call", 0xFFC0392BL, 15, true, listOf(5)),
    MEETING("type_meeting", 0xFF1E3A6EL, 60, true, listOf(15)),
    TASK("type_task", 0xFF2E7D32L, 30, false, listOf(15)),
    TRIP("type_trip", 0xFF6A4C93L, 120, false, listOf(60)),
    MAIL("type_mail", 0xFF00838FL, 15, false, listOf(15)),
    OTHER("type_other", 0xFF546E7AL, 60, false, listOf(15)),
    // Только для дней рождения из карточек контактов — выбрать его нельзя.
    // Старые события «Праздник» (метка t=BIRTHDAY) становятся обычными событиями.
    BIRTHDAY("type_birthday", 0xFFD35400L, 0, false, listOf(0));

    /** Название типа на выбранном языке. */
    val label: String get() = str(labelKey)

    /** Названия типа на всех языках — чтобы узнавать заголовки, записанные раньше на другом языке. */
    val allLabels: List<String> get() = L10n.tags.map { L10n.strIn(it, labelKey) }

    companion object {
        /** Типы, которые можно выбрать в «Новое» и в настройках. */
        val pickable: List<EventType> get() = entries.filter { it != BIRTHDAY }

        /** Тип из метки или базы. BIRTHDAY («Праздник») больше не тип — такие события обычные. */
        fun parse(s: String?): EventType? = entries.firstOrNull { it.name == s && it != BIRTHDAY }
    }
}

/** Варианты напоминаний (минуты до начала) и их подписи. */
fun reminderOptions(): List<Pair<Int, String>> = listOf(
    0 to str("rem_at_start"),
    5 to str("rem_min", 5),
    15 to str("rem_min", 15),
    30 to str("rem_min", 30),
    60 to str("rem_hour", 1),
    1440 to str("rem_one_day"),
)

fun allDayReminderLabel(m: Int): String = when {
    m <= 0 -> str("rem_allday_same", minutesToTime(-m))
    m <= 1440 -> str("rem_allday_before", minutesToTime(1440 - m))
    else -> str("rem_days_before", (m + 1439) / 1440)
}

fun reminderLabel(m: Int): String = reminderOptions().firstOrNull { it.first == m }?.second
    ?: when {
        m % 1440 == 0 -> str("rem_days", m / 1440)
        m % 60 == 0 -> str("rem_hour", m / 60)
        else -> str("rem_min", m)
    }

/** Календарь телефона (Google-аккаунт + конкретный календарь в нём) и его ограничения. */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isPrimary: Boolean,
    val writable: Boolean = true,   // можно ли туда записывать
    val synced: Boolean = true,     // синхронизируется ли с Google
    val local: Boolean = false,     // только на этом телефоне
) {
    /** Можно ли спокойно записывать: доступ есть, уходит в Google. */
    val usable: Boolean get() = writable && synced && !local

    /** Почему календарь не годится (или null, если годится). */
    val problem: String?
        get() = when {
            !writable -> str("cal_problem_readonly")
            local -> str("cal_problem_local")
            !synced -> str("cal_problem_nosync")
            else -> null
        }
}

/** Итог события — что произошло на самом деле. */
enum class Outcome(val kind: OutcomeKind) {
    DONE(OutcomeKind.GOOD),
    NO_ANSWER(OutcomeKind.BAD),
    NO_SHOW(OutcomeKind.BAD),
    NOT_DONE(OutcomeKind.BAD),
    RESCHEDULED(OutcomeKind.MOVED),
    CANCELLED(OutcomeKind.BAD);

    /** Подпись с учётом типа события: звонок "состоялся", встреча "состоялась", задача "выполнена". */
    fun label(type: EventType?): String = str(labelKey(type))

    /** Подпись на конкретном языке — чтобы узнавать итоги, записанные в описании раньше. */
    fun labelIn(tag: String, type: EventType?): String = L10n.strIn(tag, labelKey(type))

    private fun labelKey(type: EventType?): String = when (this) {
        DONE -> when (type) {
            EventType.MAIL -> "outcome_done_mail"
            EventType.CALL -> "outcome_done_call"
            EventType.MEETING -> "outcome_done_meeting"
            EventType.TASK -> "outcome_done_task"
            else -> "outcome_done_other"
        }
        NO_ANSWER -> "outcome_no_answer"
        NO_SHOW -> "outcome_no_show"
        NOT_DONE -> if (type == EventType.MAIL) "outcome_not_sent" else "outcome_not_done"
        RESCHEDULED -> when (type) {
            EventType.CALL -> "outcome_moved_call"
            EventType.MEETING -> "outcome_moved_meeting"
            else -> "outcome_moved_other"
        }
        CANCELLED -> when (type) {
            EventType.CALL -> "outcome_cancelled_call"
            EventType.MEETING, EventType.TASK -> "outcome_cancelled_f"
            else -> "outcome_cancelled_other"
        }
    }

    companion object {
        /** Какие итоги предлагать для типа. */
        fun optionsFor(type: EventType?): List<Outcome> = when (type) {
            EventType.CALL -> listOf(DONE, NO_ANSWER, RESCHEDULED, CANCELLED)
            EventType.MEETING -> listOf(DONE, RESCHEDULED, CANCELLED, NO_SHOW)
            EventType.TASK -> listOf(DONE, NOT_DONE)
            EventType.MAIL -> listOf(DONE, NOT_DONE, RESCHEDULED, CANCELLED)
            else -> listOf(DONE, CANCELLED)
        }

        fun parse(s: String?): Outcome? = entries.firstOrNull { it.name == s }

        /** Восстановить итог по подписи из строки "Итог: …" в описании события. */
        fun fromLabel(label: String): Outcome? {
            val l = label.trim().lowercase()
            return entries.firstOrNull { o ->
                L10n.tags.any { tag ->
                    (EventType.entries.map { o.labelIn(tag, it) } + o.labelIn(tag, null)).any { it.lowercase() == l }
                }
            }
        }
    }
}

/** Варианты повтора: правило RRULE (null — не повторяется) и подпись. */
fun repeatOptions(): List<Pair<String?, String>> = listOf(
    null to str("repeat_none"),
    "FREQ=DAILY" to str("repeat_daily"),
    "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" to str("repeat_weekdays"),
    "FREQ=WEEKLY" to str("repeat_weekly"),
    "FREQ=MONTHLY" to str("repeat_monthly"),
    "FREQ=YEARLY" to str("repeat_yearly"),
)

fun repeatLabel(rrule: String?): String = when (val m = matchRepeat(rrule)) {
    "" -> RepeatSpec.parse(rrule, kotlinx.datetime.LocalDate.now())?.describe() ?: str("repeat_custom")
    else -> repeatOptions().first { it.first == m }.second
}

/** Письмо: ответить на пришедшее или написать новое. */
enum class MailKind(val verbKey: String) {
    REPLY("mail_verb_reply"), NEW("mail_verb_new");

    /** Глагол на выбранном языке: «Ответить» / «Reply». */
    val verb: String get() = str(verbKey)

    /** Глагол на всех языках — чтобы узнавать заголовки, записанные раньше на другом языке. */
    val allVerbs: List<String> get() = L10n.tags.map { L10n.strIn(it, verbKey) }
}

/**
 * Письмо, привязанное к событию. Для «Ответить» — найденное письмо (по Message-ID),
 * для «Написать» — кому. peerName/peerAddr — от кого (ответ) или кому (новое).
 */
data class MailInfo(
    val kind: MailKind,
    val messageId: String? = null,
    val folder: String? = null,
    val subject: String? = null,
    val peerName: String? = null,
    val peerAddr: String? = null,
    val date: Long? = null,
    val hasDraft: Boolean = false,
) {
    /** Как показать собеседника: имя, иначе адрес. */
    val peer: String? get() = peerName?.takeIf { it.isNotBlank() } ?: peerAddr
}
