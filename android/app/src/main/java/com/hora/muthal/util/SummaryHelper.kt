package com.hora.muthal.util

import java.util.Calendar
import java.util.TimeZone

/** Ports web lib/summary.ts / ios Summary.swift — keep in sync (SPEC §5). */
object SummaryHelper {
    data class Summary(
        val totalIncome: Double,
        val totalExpense: Double,
        val balance: Double,
        val monthIncome: Double,
        val monthExpense: Double,
        val monthBalance: Double,
    )

    data class Item(val amount: Double, val type: String, val dateMillis: Long)

    /** Month membership decided by the UTC calendar month+year of [nowMillis]. */
    fun summarize(entries: List<Item>, nowMillis: Long): Summary {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = nowMillis
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)

        var ti = 0.0; var te = 0.0; var mi = 0.0; var me = 0.0
        for (e in entries) {
            cal.timeInMillis = e.dateMillis
            val inMonth = cal.get(Calendar.YEAR) == y && cal.get(Calendar.MONTH) == m
            if (e.type == "income") {
                ti += e.amount; if (inMonth) mi += e.amount
            } else if (e.type == "expense") {
                te += e.amount; if (inMonth) me += e.amount
            }
        }
        return Summary(ti, te, ti - te, mi, me, mi - me)
    }
}
