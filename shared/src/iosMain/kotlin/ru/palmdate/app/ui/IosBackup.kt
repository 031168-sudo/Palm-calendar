package ru.palmdate.app.ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import ru.palmdate.app.IosStore
import ru.palmdate.app.data.CurrentSettings
import ru.palmdate.app.str

/**
 * Резервная копия на iPhone: всё, что DateBook хранит сам (настройки, свои адреса, выбор способа связи, адреса и номера контактов,
 * почтовая программа, карты). События, их типы и итоги лежат в самом календаре (в iCloud/Google) — их копия не нужна.
 * Файл — JSON: {"format":"datebook-ios-1","data":{ключ:значение}}.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosBackup {
    private const val FORMAT = "datebook-ios-1"
    private const val PREFIX = "pd_"
    // Служебное: отчёт о сбое в копию не кладём
    private val skip = setOf("pd_last_error")
    // Делегат выбора файла нужно держать в памяти — система хранит на него только слабую ссылку
    private var picker: PickerDelegate? = null

    private fun top(): UIViewController? {
        var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        return vc
    }

    /** Собрать копию в текст JSON. */
    fun export(): String {
        val data = HashMap<Any?, Any?>()
        NSUserDefaults.standardUserDefaults.dictionaryRepresentation().forEach { (k, v) ->
            val key = k as? String ?: return@forEach
            if (!key.startsWith(PREFIX) || key in skip) return@forEach
            if (v is String || v is NSNumber || v is Number || v is Boolean || v is List<*>) data[key] = v
        }
        val root = mapOf<Any?, Any?>("format" to FORMAT, "data" to data)
        val json = NSJSONSerialization.dataWithJSONObject(root, 1u, null) ?: error("json")
        return NSString.create(data = json, encoding = NSUTF8StringEncoding)?.toString() ?: error("json")
    }

    /** Восстановить из текста. Возвращает, сколько записей восстановлено. */
    fun import(text: String): Int {
        val bytes = (text as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: error(str("err_not_backup"))
        val root = NSJSONSerialization.JSONObjectWithData(bytes, 0u, null) as? Map<*, *> ?: error(str("err_not_backup"))
        if ((root["format"] as? String)?.startsWith("datebook") != true) error(str("err_not_backup"))
        val data = root["data"] as? Map<*, *> ?: error(str("err_not_backup"))
        val d = NSUserDefaults.standardUserDefaults
        var n = 0
        data.forEach { (k, v) ->
            val key = k as? String ?: return@forEach
            if (!key.startsWith(PREFIX) || key in skip) return@forEach
            if (v is String || v is NSNumber || v is Number || v is Boolean || v is List<*>) { d.setObject(v, key); n++ }
        }
        // Настройки перечитываем сразу; язык и выбор «всегда так» подхватятся при следующем запуске
        CurrentSettings.update { IosStore.loadSettings() }
        return n
    }

    /** Показать системное окно «Поделиться / Сохранить в Файлы» с файлом копии. */
    @Suppress("CAST_NEVER_SUCCEEDS")
    fun share(fileName: String, text: String): Boolean {
        val path = NSTemporaryDirectory() + fileName
        val ok = (text as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!ok) return false
        val vc = top() ?: return false
        vc.presentViewController(UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null), true, null)
        return true
    }

    /** Окно выбора файла копии; onText — его содержимое. */
    fun pick(onText: (String?) -> Unit) {
        val vc = top() ?: return onText(null)
        val p = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
        val delegate = PickerDelegate(onText)
        picker = delegate
        p.delegate = delegate
        vc.presentViewController(p, true, null)
    }

    private class PickerDelegate(val onText: (String?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            onText(url?.let { NSString.stringWithContentsOfURL(it, NSUTF8StringEncoding, null) })
            picker = null
        }
        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { picker = null }
    }
}
