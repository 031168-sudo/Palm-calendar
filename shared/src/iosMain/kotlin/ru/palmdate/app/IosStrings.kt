package ru.palmdate.app

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import ru.palmdate.app.model.GENERATED_PLURALS
import ru.palmdate.app.model.GENERATED_STRINGS
import ru.palmdate.app.model.MapStringProvider

/** Выбор языка на iPhone: "AUTO" (как в системе), "RU" или "EN". Хранится в настройках приложения. */
object IosLang {
    private const val KEY = "app_lang"
    var mode: String = NSUserDefaults.standardUserDefaults.stringForKey(KEY) ?: "AUTO"
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
