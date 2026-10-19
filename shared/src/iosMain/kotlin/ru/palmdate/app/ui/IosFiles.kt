package ru.palmdate.app.ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDefaults
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import ru.palmdate.app.IosStore

/** Самое верхнее окно приложения — на него показываем системные окна (выбор файла, просмотр). */
internal fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

/**
 * Документы события на iPhone (билеты, брони, любые файлы). Файл копируем в память приложения (Documents/trips/<событие>/),
 * чтобы он не пропал, если исходный удалят. Список — в IosStore: «files_<событие>» = строки «id \t подпись \t имя \t путь внутри Documents».
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosFiles {
    private fun key(eventId: Long) = "files_$eventId"
    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun documentsDir(): NSURL =
        NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).firstOrNull() as NSURL

    fun has(eventId: Long): Boolean = !IosStore.getString(key(eventId)).isNullOrBlank()

    fun list(eventId: Long): List<TripDoc> =
        IosStore.getString(key(eventId)).orEmpty().lines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 4) return@mapNotNull null
            val id = p[0].toLongOrNull() ?: return@mapNotNull null
            TripDoc(id = id, eventId = eventId, label = p[1], name = p[2], path = p[3])
        }

    private fun save(eventId: Long, docs: List<TripDoc>) {
        if (docs.isEmpty()) IosStore.remove(key(eventId))
        else IosStore.putString(key(eventId), docs.joinToString("\n") { "${it.id}\t${clean(it.label)}\t${clean(it.name)}\t${it.path}" })
    }

    // Без регулярных выражений: Kotlin/Native не знает классов \p{L}
    private fun safeName(name: String) = name.map { if (it.isLetterOrDigit() || it == '.' || it == '_' || it == ' ' || it == '-') it else '_' }.joinToString("").take(80)

    /** Скопировать выбранный файл (uri — ссылка «file://…») и запомнить под подписью. */
    fun add(eventId: Long, uri: String, label: String) {
        val src = NSURL.URLWithString(uri) ?: error("bad url")
        val name = src.lastPathComponent ?: "file"
        val seq = (IosStore.getLong("file_seq") ?: 0L) + 1
        IosStore.putLong("file_seq", seq)
        val rel = "trips/$eventId/${seq}_${safeName(name)}"
        val fm = NSFileManager.defaultManager
        val dir = documentsDir().URLByAppendingPathComponent("trips/$eventId", isDirectory = true) ?: error("dir")
        fm.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val dst = documentsDir().URLByAppendingPathComponent(rel) ?: error("dst")
        if (!fm.copyItemAtURL(src, toURL = dst, error = null)) error("copy")
        save(eventId, list(eventId) + TripDoc(id = seq, eventId = eventId, label = label, name = name, path = rel))
    }

    /** События, у которых есть документы. */
    fun eventsWithFiles(): List<Long> =
        NSUserDefaults.standardUserDefaults.dictionaryRepresentation().keys.mapNotNull { k ->
            val name = k as? String ?: return@mapNotNull null
            if (!name.startsWith("pd_files_")) null else name.removePrefix("pd_files_").toLongOrNull()
        }.filter { has(it) }

    /** Содержимое файла в base64 — для резервной копии. */
    fun readBase64(doc: TripDoc): String? {
        val url = documentsDir().URLByAppendingPathComponent(doc.path) ?: return null
        return NSData.dataWithContentsOfURL(url)?.base64EncodedStringWithOptions(0u)
    }

    /** Добавить файл из резервной копии. false — такой файл у события уже есть. */
    fun addFromBackup(eventId: Long, label: String, name: String, base64: String): Boolean {
        if (list(eventId).any { it.label == label && it.name == name }) return false
        val data = NSData.create(base64EncodedString = base64, options = 0u) ?: return false
        val seq = (IosStore.getLong("file_seq") ?: 0L) + 1
        IosStore.putLong("file_seq", seq)
        val rel = "trips/$eventId/${seq}_${safeName(name)}"
        val dir = documentsDir().URLByAppendingPathComponent("trips/$eventId", isDirectory = true) ?: return false
        NSFileManager.defaultManager.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val dst = documentsDir().URLByAppendingPathComponent(rel) ?: return false
        if (!data.writeToURL(dst, atomically = true)) return false
        save(eventId, list(eventId) + TripDoc(id = seq, eventId = eventId, label = label, name = name, path = rel))
        return true
    }

    fun delete(doc: TripDoc) {
        documentsDir().URLByAppendingPathComponent(doc.path)?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
        save(doc.eventId, list(doc.eventId).filter { it.id != doc.id })
    }

    /** Событие удалено целиком — файлы убираем. */
    fun deleteAll(eventId: Long) {
        list(eventId).forEach { delete(it) }
    }

    // Системные окна держим в памяти — у делегатов слабая ссылка
    private var keep: Any? = null

    /** Показать файл (просмотр; если формат не показывается — «Поделиться»). */
    fun open(doc: TripDoc) {
        val url = documentsDir().URLByAppendingPathComponent(doc.path) ?: return
        val vc = topViewController() ?: return
        val delegate = object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
            override fun documentInteractionControllerViewControllerForPreview(controller: UIDocumentInteractionController): UIViewController = vc
        }
        val controller = UIDocumentInteractionController.interactionControllerWithURL(url)
        controller.delegate = delegate
        keep = Pair(controller, delegate)
        if (!controller.presentPreviewAnimated(true)) {
            vc.presentViewController(UIActivityViewController(activityItems = listOf(url), applicationActivities = null), true, null)
        }
    }

    private var pickerKeep: Any? = null

    /** Окно выбора файлов (можно несколько); onPicked — ссылки на копии во временной папке. */
    fun pick(onPicked: (List<String>) -> Unit) {
        val vc = topViewController() ?: return
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
        picker.allowsMultipleSelection = true
        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val urls = didPickDocumentsAtURLs.filterIsInstance<NSURL>().mapNotNull { it.absoluteString }
                // Делегат освобождаем только после обработки — иначе он уничтожается прямо внутри своего же метода
                runCatching { if (urls.isNotEmpty()) onPicked(urls) }
                    .onFailure { IosStore.putString("last_error", "picker: " + it.stackTraceToString().take(2000)) }
                pickerKeep = null
            }
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { pickerKeep = null }
        }
        pickerKeep = delegate
        picker.delegate = delegate
        vc.presentViewController(picker, true, null)
    }
}
