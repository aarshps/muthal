package com.hora.muthal.util

/** Ports web lib/categories.ts / ios Categories.swift — keep in sync (SPEC §4). */
object Categories {
    val INCOME = listOf("Donation", "Offering", "Membership", "Grant", "Other")
    val EXPENSE = listOf("Salary", "Utilities", "Maintenance", "Supplies", "Event", "Other")

    fun forType(type: String): List<String> = when (type) {
        "income" -> INCOME
        "expense" -> EXPENSE
        else -> emptyList()
    }
}
