package ru.palmdate.app.data

import ru.palmdate.app.R
import ru.palmdate.app.str
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Документы выезда (билеты, посадочные, брони). Файл копируем во внутреннюю память приложения,
 * чтобы он не пропал, если исходный файл удалят. В базе — подпись и путь.
 */
class TripFiles(private val context: Context, private val dao: LinkDao) {

    suspend fun list(eventId: Long): List<TripFile> = dao.filesFor(eventId)

    /** Скопировать файл из выбранного места и запомнить его под подписью [label]. */
    suspend fun add(eventId: Long, uri: Uri, label: String): TripFile {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: str(R.string.trip_file_default_name)
        val dir = File(context.filesDir, "trips/$eventId").apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString() + "_" + safeName(name))
        val input = resolver.openInputStream(uri) ?: error(str(R.string.err_open_file))
        input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
        val row = TripFile(
            eventId = eventId, label = label, name = name,
            path = file.absolutePath, mime = resolver.getType(uri),
        )
        return row.copy(id = dao.insertFile(row))
    }

    suspend fun delete(f: TripFile) {
        File(f.path).delete()
        dao.deleteFile(f.id)
    }

    companion object {
        /** Имя файла без странных символов, чтобы его спокойно записать на диск. */
        fun safeName(name: String) = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80)

        /** Ссылка, по которой другие приложения (PDF-читалка, почта) откроют файл. */
        fun uriFor(context: Context, f: TripFile): Uri =
            FileProvider.getUriForFile(context, context.packageName + ".files", File(f.path))
    }
}
