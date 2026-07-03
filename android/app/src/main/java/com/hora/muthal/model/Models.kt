package com.hora.muthal.model

/** Canonical models — port web lib/types.ts / ios Models (SPEC §1). Multi-user
 * institutions with roles: see SPEC §3 for the role matrix.
 * No-arg defaults keep Firestore's reflective deserialization + R8 happy. */
data class Institution(
    var id: String = "",
    var name: String = "",
    var type: String = "Temple",
    var currency: String = "INR",
    var code: String = "",
    var ownerId: String = "",
    var createdAt: Long = 0,
)

/** A user's role within one institution (SPEC §3). */
object Role {
    const val OWNER = "owner"
    const val ADMIN = "admin"
    const val MEMBER = "member"
}

data class Member(
    var uid: String = "",
    var role: String = Role.MEMBER,
    var displayName: String = "",
    var email: String = "",
    var photoUrl: String = "",
    var joinedAt: Long = 0,
) {
    val isOwner get() = role == Role.OWNER
    val isAdminOrOwner get() = role == Role.OWNER || role == Role.ADMIN
}

/** A user's index entry for one institution they belong to (SPEC §1), with just
 * enough denormalized institution data to render the switcher without extra reads. */
data class Membership(
    var institutionId: String = "",
    var role: String = Role.MEMBER,
    var institutionName: String = "",
    var institutionType: String = "Temple",
    var currency: String = "INR",
) {
    val isAdminOrOwner get() = role == Role.OWNER || role == Role.ADMIN
    val isOwner get() = role == Role.OWNER
}

data class Category(
    var id: String = "",
    var name: String = "",
    var kind: String = "income",
)

data class Entry(
    var id: String = "",
    var date: Long = 0,
    var amount: Double = 0.0,
    var type: String = "income",
    var category: String = "",
    var note: String = "",
    var createdBy: String = "",
    var createdAt: Long = 0,
)
