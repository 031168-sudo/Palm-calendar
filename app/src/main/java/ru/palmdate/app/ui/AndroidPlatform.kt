package ru.palmdate.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.core.content.FileProvider
import kotlinx.datetime.LocalDate
import ru.palmdate.app.data.CallOptions
import ru.palmdate.app.data.OwnAddresses
import ru.palmdate.app.data.toJava
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.model.RepeatSpec
import java.io.File

/** Что экраны просят у Android: карты, открыть файл, хранилища, окно своего повтора. */
class AndroidPlatform(private val ctx: Context) : PlatformActions {
    override val ownAddresses: OwnAddressStore by lazy { OwnAddresses(ctx) }
    override val callPrefs: CallPrefsStore by lazy { CallOptions(ctx) }

    override fun openMap(address: String) = ctx.openMap(address)

    override fun runPrimaryAction(e: PalmEvent, call: (ContactRef) -> Unit, mail: (PalmEvent) -> Unit) =
        ctx.runPrimaryAction(e, call, mail)

    override fun openInCalendar(e: PalmEvent) = ctx.openInCalendar(e)

    override fun fileName(uri: String): String = runCatching {
        ctx.contentResolver.query(android.net.Uri.parse(uri), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: android.net.Uri.parse(uri).lastPathSegment ?: uri

    override fun openDocument(doc: TripDoc) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(doc.path))
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, doc.mime ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    @Composable
    override fun RepeatDialog(rrule: String?, anchor: LocalDate, allDay: Boolean, onDismiss: () -> Unit, onResult: (String?) -> Unit) {
        val a = anchor.toJava()
        ru.palmdate.app.ui.RepeatDialog(
            initial = RepeatSpec.parse(rrule, a) ?: RepeatSpec.defaultFor(a),
            anchor = a, allDay = allDay, onDismiss = onDismiss, onSave = { onResult(it) },
        )
    }
}
