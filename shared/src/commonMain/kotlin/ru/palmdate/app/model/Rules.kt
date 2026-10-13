package ru.palmdate.app.model

// Чистая логика без Android и без строк интерфейса: общий код для Android и iOS.

/** Время дня из минут: 540 → "9:00". */
fun minutesToTime(m: Int): String = "${m / 60}:${(m % 60).toString().padStart(2, '0')}"

/**
 * Напоминания для событий на весь день. Android/Google считают их от полуночи начала дня:
 * отрицательные минуты — "в этот день в ...", положительные — "накануне в ...".
 */
val ALLDAY_REMINDER_OPTIONS: List<Int> = listOf(-480, -540, -720, 360)

/**
 * Ссылка на контакт из телефонной книги. Храним LOOKUP_KEY — он переживает слияние контактов.
 * phone — номер, выбранный для этого события/контакта.
 */
data class ContactRef(
    val lookupKey: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
)

data class PhoneNumber(val number: String, val label: String)

/** Как отображать итог: зелёная галочка, красный крестик или стрелка переноса. */
enum class OutcomeKind { GOOD, BAD, MOVED }

/**
 * К какому из наших вариантов относится правило повтора события (в т.ч. созданного в Google).
 * Возвращает правило-вариант, null — не повторяется, "" — своё правило (с окончанием, интервалом и т.п.).
 */
fun matchRepeat(rrule: String?): String? {
    if (rrule.isNullOrBlank()) return null
    val parts = rrule.uppercase().split(";").filter { it.isNotBlank() && !it.startsWith("WKST=") }
        .associate { it.substringBefore("=") to it.substringAfter("=") }
    if (parts.keys.any { it !in setOf("FREQ", "BYDAY", "BYMONTHDAY", "BYMONTH") }) return ""
    val byDay = parts["BYDAY"]
    return when (parts["FREQ"]) {
        "DAILY" -> if (byDay == null) "FREQ=DAILY" else ""
        "WEEKLY" -> when {
            byDay == null || !byDay.contains(",") -> "FREQ=WEEKLY"
            byDay.split(",").toSet() == setOf("MO", "TU", "WE", "TH", "FR") -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
            else -> ""
        }
        "MONTHLY" -> "FREQ=MONTHLY"
        "YEARLY" -> "FREQ=YEARLY"
        else -> ""
    }
}

/** Строка статистики: контакт и сколько с ним состоялось звонков, встреч и отправлено писем. */
data class ContactStat(val contact: ContactRef, val calls: Int, val meetings: Int, val mails: Int = 0) {
    val total get() = calls + meetings + mails
}

/** Что удалить у повторяющегося события. */
enum class DeleteScope { ONE, FOLLOWING, ALL }
