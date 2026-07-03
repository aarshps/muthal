package com.hora.muthal.util

import java.util.Calendar
import java.util.TimeZone

/** Ports web lib/summary.ts / ios Summary.swift — keep in sync (SPEC §6-7). */
object SummaryHelper {
    data class Summary(
        val totalIncome: Double,
        val totalExpense: Double,
        val balance: Double,
        val monthIncome: Double,
        val monthExpense: Double,
        val monthBalance: Double,
    )

    data class PeriodSummary(
        val openingBalance: Double,
        val periodIncome: Double,
        val periodExpense: Double,
        val closingBalance: Double,
        val entryCount: Int,
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

    /** Opening/closing balance + in-range totals for [startMillis, endInclusiveMillis]
     * (SPEC §7). Entries strictly before the window feed only the opening balance;
     * entries strictly after the window are excluded entirely. */
    fun periodSummarize(entries: List<Item>, startMillis: Long, endInclusiveMillis: Long): PeriodSummary {
        var opening = 0.0; var pi = 0.0; var pe = 0.0; var count = 0
        for (e in entries) {
            when {
                e.dateMillis < startMillis -> {
                    if (e.type == "income") opening += e.amount
                    else if (e.type == "expense") opening -= e.amount
                }
                e.dateMillis in startMillis..endInclusiveMillis -> {
                    count++
                    if (e.type == "income") pi += e.amount
                    else if (e.type == "expense") pe += e.amount
                }
            }
        }
        return PeriodSummary(opening, pi, pe, opening + pi - pe, count)
    }
}
