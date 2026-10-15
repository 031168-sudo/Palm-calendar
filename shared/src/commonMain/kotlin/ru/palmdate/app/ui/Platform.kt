package ru.palmdate.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.PalmEvent

/** Документ события (билет, бронь…). Файл лежит в памяти приложения, path — где. */
data class TripDoc(
    val id: Long = 0,
    val eventId: Long,
    val label: String,
    val name: String,
    val path: String,
    val mime: String? = null,
)

/** Свой адрес события, введённый вручную (хранится отдельно от поля «Место»). */
interface OwnAddressStore {
    fun get(eventId: Long): String?
    fun set(eventId: Long, address: String?)
}

/** Запомненный способ связи с человеком («всегда так»). */
interface CallPrefsStore {
    fun rememberedLabel(lookupKey: String): String?
    fun remember(lookupKey: String, key: String?)
}

/**
 * Всё, что экраны просят у системы: карты, открытие файла, хранилища. На Android — AndroidPlatform,
 * на iOS будет своя реализация.
 */
interface PlatformActions {
    val ownAddresses: OwnAddressStore
    val callPrefs: CallPrefsStore
    fun openMap(address: String)
    fun openDocument(doc: TripDoc)

    /** Тап по иконке события: позвонить / маршрут / письмо; для остальных — открыть в системном календаре. */
    fun runPrimaryAction(e: PalmEvent, call: (ContactRef) -> Unit, mail: (PalmEvent) -> Unit)

    /** Открыть событие в системном календаре. */
    fun openInCalendar(e: PalmEvent)

    /** Окно «Настроить…» для своего правила повтора. onResult — новое правило (RRULE) или null. */
    @Composable
    fun RepeatDialog(rrule: String?, anchor: LocalDate, allDay: Boolean, onDismiss: () -> Unit, onResult: (String?) -> Unit)
}

private object NoPlatform : PlatformActions {
    override val ownAddresses = object : OwnAddressStore {
        override fun get(eventId: Long): String? = null
        override fun set(eventId: Long, address: String?) {}
    }
    override val callPrefs = object : CallPrefsStore {
        override fun rememberedLabel(lookupKey: String): String? = null
        override fun remember(lookupKey: String, key: String?) {}
    }
    override fun openMap(address: String) {}
    override fun openDocument(doc: TripDoc) {}
    override fun runPrimaryAction(e: PalmEvent, call: (ContactRef) -> Unit, mail: (PalmEvent) -> Unit) {}
    override fun openInCalendar(e: PalmEvent) {}

    @Composable
    override fun RepeatDialog(rrule: String?, anchor: LocalDate, allDay: Boolean, onDismiss: () -> Unit, onResult: (String?) -> Unit) {}
}

val LocalPlatform = staticCompositionLocalOf<PlatformActions> { NoPlatform }

/** Кнопка «Назад» закрывает окно (на iOS — ничего не делает). */
@Composable
expect fun PlatformBackHandler(onBack: () -> Unit)

/** Поле ввода уезжает над клавиатурой. */
@Composable
expect fun Modifier.keepAboveKeyboard(): Modifier

/** Выбор файлов: возвращает функцию «открыть выбор»; результат — адреса выбранных файлов в виде строк. */
@Composable
expect fun rememberDocumentPicker(onPicked: (List<String>) -> Unit): () -> Unit
