package ru.palmdate.app.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MapStringsTest {
    private val p = MapStringProvider(
        strings = mapOf(
            "en" to mapOf("hi" to "Hello %s", "two" to "%2\$s then %1\$s", "pct" to "100%%"),
            "ru" to mapOf("hi" to "Привет, %s"),
        ),
        plurals = mapOf(
            "ru" to mapOf("calls" to mapOf("one" to "%d звонок", "few" to "%d звонка", "many" to "%d звонков", "other" to "%d звонка")),
            "en" to mapOf("calls" to mapOf("one" to "%d call", "other" to "%d calls")),
        ),
        currentLanguage = { "ru" },
    )

    @Test fun formatting() {
        assertEquals("Привет, Иван", p.str("hi", "Иван"))
        assertEquals("Hello Ann", p.strIn("en", "hi").replace("%s", "Ann"))
        assertEquals("b then a", MapStringProvider.format("%2\$s then %1\$s", arrayOf("a", "b")))
        assertEquals("100%", p.strIn("en", "pct"))
        assertEquals("zz", p.str("zz")) // нет такой строки — вернётся ключ
    }

    @Test fun russianPlurals() {
        val expect = mapOf(1 to "1 звонок", 2 to "2 звонка", 5 to "5 звонков", 11 to "11 звонков", 21 to "21 звонок", 22 to "22 звонка", 25 to "25 звонков", 111 to "111 звонков", 0 to "0 звонков")
        expect.forEach { (n, s) -> assertEquals(s, p.plural("calls", n), "n=$n") }
    }
}
