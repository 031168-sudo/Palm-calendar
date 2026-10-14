package ru.palmdate.app.model

/** Человек с адресом почты — для поля «Кому» и для «Написать». */
data class EmailContact(val lookupKey: String, val name: String, val email: String)
