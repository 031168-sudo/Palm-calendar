package ru.palmdate.app.data

// Календарь Android считает по java.time (зоны, миллисекунды); экраны и модель — на kotlinx-datetime.
// Здесь — перевод туда и обратно на границе репозитория.

fun java.time.LocalDate.toKx(): kotlinx.datetime.LocalDate = kotlinx.datetime.LocalDate(year, monthValue, dayOfMonth)

fun kotlinx.datetime.LocalDate.toJava(): java.time.LocalDate = java.time.LocalDate.of(year, monthNumber, dayOfMonth)

fun java.time.LocalDateTime.toKx(): kotlinx.datetime.LocalDateTime =
    kotlinx.datetime.LocalDateTime(year, monthValue, dayOfMonth, hour, minute, second, nano)

fun kotlinx.datetime.LocalDateTime.toJava(): java.time.LocalDateTime =
    java.time.LocalDateTime.of(year, monthNumber, dayOfMonth, hour, minute, second, nanosecond)
