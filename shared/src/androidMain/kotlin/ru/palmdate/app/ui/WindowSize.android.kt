package ru.palmdate.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
actual fun windowWidthDp(): Int = LocalConfiguration.current.screenWidthDp
