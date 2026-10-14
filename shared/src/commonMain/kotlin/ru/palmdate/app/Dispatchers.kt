package ru.palmdate.app

import kotlinx.coroutines.CoroutineDispatcher

/** Поток для работы с диском и системой (на Android — IO, на iOS — общий пул). */
expect val ioDispatcher: CoroutineDispatcher
