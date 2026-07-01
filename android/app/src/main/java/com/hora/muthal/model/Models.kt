package com.hora.muthal.model

/** Canonical models — port web lib/types.ts / ios Models (SPEC §1).
 * No-arg defaults keep Firestore's reflective deserialization + R8 happy. */
data class Institution(
    var id: String = "",
    var name: String = "",
    var type: String = "Temple",
    var currency: String = "INR",
    var createdAt: Long = 0,
)

data class Entry(
    var id: String = "",
    var date: Long = 0,
    var amount: Double = 0.0,
    var type: String = "income",
    var category: String = "",
    var note: String = "",
    var institutionId: String = "",
    var institutionName: String = "",
    var currency: String = "INR",
    var userId: String = "",
)
