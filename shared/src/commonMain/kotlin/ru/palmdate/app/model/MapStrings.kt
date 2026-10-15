package ru.palmdate.app.model

/**
 * Тексты интерфейса из готовых таблиц (ключ → строка по языкам). Нужен там, где нет Android-ресурсов (iOS):
 * таблицы собираются из тех же strings.xml, так что русский и English совпадают с Android.
 */
class MapStringProvider(
    private val strings: Map<String, Map<String, String>>,                 // язык → ключ → строка
    private val plurals: Map<String, Map<String, Map<String, String>>>,    // язык → ключ → количество → строка
    private val currentLanguage: () -> String,
    override val tags: List<String> = listOf("ru", "en"),
) : StringProvider {

    override val language: String get() = currentLanguage()

    private fun lookup(tag: String, key: String): String? = strings[tag]?.get(key) ?: strings["en"]?.get(key)

    override fun str(key: String, vararg args: Any): String = strIn(language, key, *args)

    override fun strIn(tag: String, key: String): String = strIn(tag, key, *emptyArray())

    private fun strIn(tag: String, key: String, vararg args: Any): String {
        val raw = lookup(tag, key) ?: return key
        return if (args.isEmpty()) raw.replace("%%", "%") else format(raw, args)
    }

    override fun plural(key: String, n: Int): String {
        val byQty = plurals[language]?.get(key) ?: plurals["en"]?.get(key) ?: return key
        val text = byQty[quantity(language, n)] ?: byQty["other"] ?: return key
        return format(text, arrayOf(n))
    }

    companion object {
        /** Правила множественного числа (CLDR) для русского и английского. */
        fun quantity(lang: String, n: Int): String {
            if (lang != "ru") return if (n == 1) "one" else "other"
            val m10 = n % 10
            val m100 = n % 100
            return when {
                m10 == 1 && m100 != 11 -> "one"
                m10 in 2..4 && m100 !in 12..14 -> "few"
                m10 == 0 || m10 in 5..9 || m100 in 11..14 -> "many"
                else -> "other"
            }
        }

        /** Подстановка %s, %d, %1$s, %2$d и %% в шаблон. */
        fun format(pattern: String, args: Array<out Any>): String {
            val sb = StringBuilder()
            var i = 0
            var next = 0
            while (i < pattern.length) {
                val c = pattern[i]
                if (c != '%') { sb.append(c); i++; continue }
                if (i + 1 < pattern.length && pattern[i + 1] == '%') { sb.append('%'); i += 2; continue }
                var j = i + 1
                var num = -1
                val digitsStart = j
                while (j < pattern.length && pattern[j].isDigit()) j++
                if (j < pattern.length && pattern[j] == '$' && j > digitsStart) {
                    num = pattern.substring(digitsStart, j).toInt() - 1
                    j++
                } else j = digitsStart
                // флаги и ширина (например %02d) — пропускаем
                while (j < pattern.length && (pattern[j].isDigit() || pattern[j] == '-' || pattern[j] == '.')) j++
                if (j < pattern.length && (pattern[j] == 's' || pattern[j] == 'd')) {
                    val idx = if (num >= 0) num else next++
                    sb.append(args.getOrNull(idx)?.toString() ?: "")
                    i = j + 1
                } else { sb.append(c); i++ }
            }
            return sb.toString()
        }
    }
}
