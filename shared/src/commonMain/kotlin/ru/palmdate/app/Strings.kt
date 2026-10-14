package ru.palmdate.app

import ru.palmdate.app.model.L10n

/** Строка интерфейса по ключу (имя в strings.xml) на выбранном языке. */
fun str(key: String, vararg args: Any): String = L10n.str(key, *args)

/** Строка с числом по ключу: «3 звонка» / «3 calls». */
fun plu(key: String, n: Int): String = L10n.plural(key, n)
