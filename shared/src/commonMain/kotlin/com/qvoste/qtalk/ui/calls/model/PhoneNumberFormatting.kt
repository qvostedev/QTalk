package com.qvoste.qtalk.ui.calls.model

internal fun formatPhoneNumber(number: String): String {
    val value = number.trim()
    val digits = value.filter(Char::isDigit)
    if (digits.length <= 4 || value.any { it == '*' || it == '#' }) return value

    return when (digits.length) {
        10 -> "+7 ${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6, 8)} ${digits.substring(8, 10)}"
        11 -> "+${digits[0]} ${digits.substring(1, 4)} ${digits.substring(4, 7)} ${digits.substring(7, 9)} ${digits.substring(9, 11)}"
        else -> value
    }
}

internal fun dialablePhoneNumber(number: String): String =
    number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

fun Contact.matchesCallNumber(number: String): Boolean {
    fun normalized(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length == 11 && digits.startsWith('8')) "7${digits.drop(1)}" else digits
    }
    val target = normalized(number)
    return target.isNotEmpty() && sequenceOf(this.number, phone)
        .any { normalized(it) == target }
}
