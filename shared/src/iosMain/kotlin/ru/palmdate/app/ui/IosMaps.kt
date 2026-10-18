package ru.palmdate.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIApplication
import ru.palmdate.app.IosStore
import ru.palmdate.app.str
import ru.palmdate.app.ui.theme.Palm

/** Программа карт: probe — схема для проверки «установлена ли» (прописана в Info.plist), url — как открыть адрес. */
internal class MapApp(val key: String, val label: String, val probe: String, val url: (String) -> String)

// q — адрес, уже закодированный для ссылки
private val MAP_APPS = listOf(
    MapApp("apple", "Apple Карты", "maps://") { q -> "http://maps.apple.com/?q=$q" },
    MapApp("yandexmaps", "Яндекс Карты", "yandexmaps://") { q -> "yandexmaps://maps.yandex.ru/?text=$q" },
    MapApp("yandexnavi", "Яндекс Навигатор", "yandexnavi://") { q -> "yandexnavi://map_search?text=$q" },
    MapApp("2gis", "2ГИС", "dgis://") { q -> "dgis://2gis.ru/search/$q" },
    MapApp("google", "Google Карты", "comgooglemaps://") { q -> "comgooglemaps://?q=$q" },
)

private fun canOpen(url: String): Boolean = NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.canOpenURL(it) } ?: false

/** Apple Карты есть всегда. */
internal fun installedMapApps(): List<MapApp> = MAP_APPS.filter { it.key == "apple" || canOpen(it.probe) }

internal object IosMapPrefs {
    fun remembered(): MapApp? = IosStore.getString("map_app")?.let { k -> installedMapApps().firstOrNull { it.key == k } }
    fun set(app: MapApp?) { if (app == null) IosStore.remove("map_app") else IosStore.putString("map_app", app.key) }
}

@Suppress("CAST_NEVER_SUCCEEDS")
private fun pct(s: String): String =
    (s as NSString).stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.URLQueryAllowedCharacterSet) ?: s

private fun launch(app: MapApp, address: String) {
    val u = NSURL.URLWithString(app.url(pct(address))) ?: return
    UIApplication.sharedApplication.openURL(u, emptyMap<Any?, Any?>(), null)
}

internal object IosMaps {
    /** Адрес, для которого надо спросить программу карт (null — ничего не спрашиваем). */
    internal var pending by mutableStateOf<String?>(null)

    fun open(address: String) {
        val list = installedMapApps()
        val r = IosMapPrefs.remembered()
        when {
            r != null -> launch(r, address)
            list.size <= 1 -> launch(list.firstOrNull() ?: MAP_APPS.first(), address)
            else -> pending = address
        }
    }
}

/** Окно выбора карт: показывается, когда стоит больше одной программы и выбор ещё не запомнен. */
@Composable
fun IosMapHost(content: @Composable () -> Unit) {
    content()
    IosMaps.pending?.let { address ->
        val list = remember(address) { installedMapApps() }
        var always by remember { mutableStateOf(true) }
        val close = { IosMaps.pending = null }
        AlertDialog(
            onDismissRequest = close,
            containerColor = Palm.paper,
            title = { Text(str("mapui_title"), style = Palm.title, color = Palm.ink) },
            text = {
                Column {
                    Text(address, style = Palm.small, color = Palm.inkSoft, maxLines = 2, modifier = Modifier.padding(bottom = 4.dp))
                    list.forEach { a ->
                        Text(
                            a.label, style = Palm.body, color = Palm.ink,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { if (always) IosMapPrefs.set(a); close(); launch(a, address) }
                                .dottedRule().padding(vertical = 12.dp),
                        )
                    }
                    Row(Modifier.fillMaxWidth().clickable { always = !always }.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = always, onCheckedChange = { always = it })
                        Text(str("mailui_always_open"), style = Palm.body, color = Palm.ink)
                    }
                    Text(str("mapui_change_in_settings"), style = Palm.small, color = Palm.inkSoft)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = close) { Text(str("cancel")) } },
        )
    }
}
