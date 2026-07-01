package com.hora.muthal.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Ports web lib/currency.ts / ios Currency.swift — keep in sync (SPEC §3). */
object CurrencyHelper {
    data class CurrencyItem(val code: String, val name: String, val symbol: String)

    val CURRENCIES: List<CurrencyItem> = listOf(
        CurrencyItem("INR", "Indian Rupee", "₹"),
        CurrencyItem("USD", "US Dollar", "$"),
        CurrencyItem("EUR", "Euro", "€"),
        CurrencyItem("GBP", "British Pound", "£"),
        CurrencyItem("JPY", "Japanese Yen", "¥"),
        CurrencyItem("AUD", "Australian Dollar", "$"),
        CurrencyItem("CAD", "Canadian Dollar", "$"),
        CurrencyItem("CHF", "Swiss Franc", "₣"),
        CurrencyItem("CNY", "Chinese Yuan", "¥"),
        CurrencyItem("HKD", "Hong Kong Dollar", "$"),
        CurrencyItem("NZD", "New Zealand Dollar", "$"),
        CurrencyItem("SEK", "Swedish Krona", "kr"),
        CurrencyItem("KRW", "South Korean Won", "₩"),
        CurrencyItem("SGD", "Singapore Dollar", "$"),
        CurrencyItem("MXN", "Mexican Peso", "$"),
        CurrencyItem("KES", "Kenyan Shilling", "KSh"),
        CurrencyItem("UNT", "Generic Unit", "#"),
    )

    fun symbol(code: String): String = CURRENCIES.firstOrNull { it.code == code }?.symbol ?: "$"

    /** "₹ 649" — integer when whole, else two decimals; sign kept as-is. */
    fun format(amount: Double, code: String): String {
        val sym = symbol(code)
        val rounded = if (amount % 1.0 == 0.0) amount.toLong().toString()
        else String.format(Locale.US, "%.2f", amount)
        return "$sym $rounded"
    }

    /** Compact form using k / l (lakh) / m suffixes (Indian-influenced). */
    fun compact(amount: Double): String {
        if (amount == 0.0) return "0"
        val a = abs(amount)
        return when {
            a >= 1_000_000 -> trim(amount / 1_000_000, "m")
            a >= 100_000 -> trim(amount / 100_000, "l")
            a >= 1_000 -> trim(amount / 1_000, "k")
            else -> amount.roundToLong().toString()
        }
    }

    private fun trim(value: Double, suffix: String): String {
        val formatted = String.format(Locale.US, "%.1f", value)
        return if (formatted.endsWith(".0")) formatted.dropLast(2) + suffix else formatted + suffix
    }
}
