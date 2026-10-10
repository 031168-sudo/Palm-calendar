package ru.palmdate.app.ui

import android.graphics.Rect
import androidx.compose.runtime.compositionLocalOf

/**
 * Сгиб складного телефона, как его сообщает Android (Jetpack WindowManager).
 * bounds — в пикселях окна. tabletop — полусложен, сгиб горизонтальный («ноутбук» на столе);
 * vertical — сгиб вертикальный (книжка).
 */
data class Hinge(val tabletop: Boolean, val vertical: Boolean, val bounds: Rect)

/** null — сгиба нет (обычный телефон, планшет) или он не мешает. */
val LocalHinge = compositionLocalOf<Hinge?> { null }
