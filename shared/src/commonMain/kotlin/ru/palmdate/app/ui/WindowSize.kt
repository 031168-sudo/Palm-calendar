package ru.palmdate.app.ui

import androidx.compose.runtime.Composable

/** Ширина окна в dp (на Android — из конфигурации, на iOS — из размера экрана). */
@Composable
expect fun windowWidthDp(): Int
