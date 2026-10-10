package ru.palmdate.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/** Язык интерфейса, как его выбирают в «Настройки → Вид → Язык». */
enum class LangMode { AUTO, RU, EN }

/**
 * Язык приложения: «Авто» (русский на русскоязычном телефоне, иначе English), «Русский» или «English».
 * Выбор хранится отдельно от остальных настроек — он нужен до того, как они загружены.
 *
 * Тексты берутся через [str] и [plu] из любого места (в том числе не из экранов: заголовки событий,
 * сообщения об ошибках, напоминания). Экран при смене языка пересоздаётся.
 */
object Lang {
    private const val PREFS = "app_lang"
    private const val KEY = "mode"

    private var app: Context? = null

    @Volatile var mode: LangMode = LangMode.AUTO
        private set

    @Volatile var locale: Locale = Locale.forLanguageTag("ru")
        private set

    @Volatile private var localized: Context? = null

    /** Запомнить контекст приложения и прочитать сохранённый выбор. Можно вызывать повторно. */
    fun init(context: Context) {
        val appCtx = context.applicationContext ?: context
        if (app !== appCtx) { app = appCtx; cache.clear() }
        mode = runCatching {
            LangMode.valueOf(appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: "AUTO")
        }.getOrDefault(LangMode.AUTO)
        apply()
    }

    /** Выбрать язык. [persist] = false — не запоминать (для тестов). */
    fun setMode(m: LangMode, persist: Boolean = true) {
        mode = m
        if (persist) {
            app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, m.name)?.apply()
        }
        apply()
    }

    /** Какой язык получится при выбранном режиме: «Авто» смотрит на язык телефона. */
    fun resolve(m: LangMode, system: Locale = systemLocale()): Locale = when (m) {
        LangMode.RU -> Locale.forLanguageTag("ru")
        LangMode.EN -> Locale.forLanguageTag("en")
        LangMode.AUTO -> if (system.language == "ru") Locale.forLanguageTag("ru") else Locale.forLanguageTag("en")
    }

    private fun systemLocale(): Locale = Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()

    private fun apply() {
        locale = resolve(mode)
        val base = app ?: return
        val cfg = Configuration(base.resources.configuration).apply { setLocale(this@Lang.locale) }
        localized = base.createConfigurationContext(cfg)
    }

    /** Языки, на которых могли быть записаны заголовки и итоги событий раньше. */
    val TAGS = listOf("ru", "en")

    private val cache = java.util.concurrent.ConcurrentHashMap<Pair<String, Int>, String>()

    /** Строка на конкретном языке (не на выбранном) — чтобы узнавать заголовки и итоги, записанные раньше. */
    fun strIn(tag: String, @StringRes id: Int): String = cache.getOrPut(tag to id) {
        val base = app ?: error("Lang.init не вызван")
        val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        base.createConfigurationContext(cfg).getString(id)
    }

    /** Контекст с выбранным языком — для строк и количеств. */
    val context: Context
        get() {
            val c = localized ?: return app ?: error("Lang.init не вызван")
            // Если система сбросила язык у готового контекста (бывает при смене настроек телефона) — собираем заново
            if (c.resources.configuration.locales.get(0)?.language != locale.language) {
                apply()
                return localized ?: c
            }
            return c
        }

    /** Конфигурация для окна приложения (см. MainActivity). */
    fun overrideConfiguration(): Configuration = Configuration().apply { setLocale(this@Lang.locale) }
}

/** Application: язык нужен ещё до первого экрана (напоминания, перезагрузка телефона). */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.init(this)
    }
}

/** Строка из ресурсов на выбранном языке. */
fun str(@StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) Lang.context.getString(id) else Lang.context.getString(id, *args)

/** Строка с числом: «3 звонка» / «3 calls». В тексте ресурса число — %d. */
fun plu(@PluralsRes id: Int, n: Int): String = Lang.context.resources.getQuantityString(id, n, n)
