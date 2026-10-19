package ru.palmdate.app.model

/**
 * Откуда общий код берёт тексты интерфейса. Ключ — имя строки (`type_call`, `rem_min`…).
 * На Android это strings.xml (русский и English), на iOS позже — свой источник с теми же ключами.
 */
interface StringProvider {
    /** Строка на выбранном языке; аргументы подставляются в %d / %s. */
    fun str(key: String, vararg args: Any): String

    /** Строка на конкретном языке — чтобы узнавать заголовки и итоги, записанные раньше. */
    fun strIn(tag: String, key: String): String

    /** Языки, на которых могли быть записаны заголовки и итоги. */
    val tags: List<String>

    /** Выбранный язык интерфейса: "ru" или "en". */
    val language: String

    /** Строка с числом: «3 звонка» / «3 calls»; число подставляется в %d. */
    fun plural(key: String, n: Int): String

    /** Список строк (названия дней недели и т. п.) на выбранном языке; числовые списки — числами в виде строк. */
    fun array(key: String): List<String>
}

object L10n {
    var provider: StringProvider? = null

    private fun p(): StringProvider = provider ?: error("L10n.provider не задан")

    fun str(key: String, vararg args: Any): String = p().str(key, *args)
    fun strIn(tag: String, key: String): String = p().strIn(tag, key)
    val tags: List<String> get() = p().tags
    val language: String get() = p().language
    fun plural(key: String, n: Int): String = p().plural(key, n)
    fun array(key: String): List<String> = p().array(key)
}
