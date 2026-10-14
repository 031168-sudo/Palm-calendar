package ru.palmdate.app.ui

import androidx.compose.runtime.compositionLocalOf

/** Границы сгиба в пикселях окна. */
data class HingeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Сгиб складного телефона, как его сообщает система (на Android — Jetpack WindowManager).
 * tabletop — полусложен, сгиб горизонтальный («ноутбук» на столе); vertical — сгиб вертикальный (книжка).
 */
data class Hinge(val tabletop: Boolean, val vertical: Boolean, val bounds: HingeBounds)

/** null — сгиба нет (обычный телефон, планшет) или он не мешает. */
val LocalHinge = compositionLocalOf<Hinge?> { null }
