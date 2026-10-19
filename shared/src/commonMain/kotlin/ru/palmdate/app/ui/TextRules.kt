package ru.palmdate.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization

/** Первая буква записи — заглавная (названия, адреса, заметки). */
fun String.capFirst(): String = replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }

/** Клавиатура сама предлагает заглавную в начале записи. */
val SentenceCaps = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
