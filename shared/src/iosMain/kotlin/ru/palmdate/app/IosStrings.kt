package ru.palmdate.app

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import ru.palmdate.app.model.GENERATED_ARRAYS
import ru.palmdate.app.model.GENERATED_PLURALS
import ru.palmdate.app.model.GENERATED_STRINGS
import ru.palmdate.app.model.MapStringProvider

/** Выбор языка на iPhone: "AUTO" (как в системе), "RU" или "EN". Хранится в настройках приложения. */
object IosLang {
    private const val KEY = "app_lang"
    // Состояние Compose: любой экран, который при отрисовке читает язык (str(...), переключатель в настройках),
    // перерисуется сам при смене — без этого кнопка не подсвечивалась, а тексты менялись только после перезапуска окна.
    // По умолчанию — «Авто» (язык телефона).
    var mode: String by androidx.compose.runtime.mutableStateOf(NSUserDefaults.standardUserDefaults.stringForKey(KEY) ?: "AUTO")
        private set

    fun setMode(m: String) {
        mode = m
        NSUserDefaults.standardUserDefaults.setObject(m, KEY)
    }

    /** "ru" или "en". Авто: русский, если системный язык русский, иначе English. */
    val language: String
        get() = when (mode) {
            "RU" -> "ru"
            "EN" -> "en"
            else -> if ((NSLocale.preferredLanguages.firstOrNull() as? String)?.startsWith("ru") == true) "ru" else "en"
        }
}

val IosStrings = MapStringProvider(GENERATED_STRINGS, GENERATED_PLURALS, currentLanguage = { IosLang.language }, arrays = GENERATED_ARRAYS)
