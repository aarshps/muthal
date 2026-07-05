package com.hora.muthal.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.hora.muthal.model.Category
import com.hora.muthal.model.Entry
import com.hora.muthal.model.Institution
import com.hora.muthal.model.Member
import com.hora.muthal.model.Membership
import com.hora.muthal.model.Role
import com.hora.muthal.util.Categories
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Firestore data layer for the v2 multi-user institution model (SPEC §1-3). Institutions
 * are top-level documents shared by members with a role each; join is by a 6-character
 * code. Institution creation and joining are SEQUENCES of awaited writes (not batches) —
 * see the security-rules comments in shared/firebase/firestore.rules for why a batch
 * can't be used here (rules can't see sibling writes within the same batch).
 */
class FirestoreRepo(private val uid: String) {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    private fun institutions() = db.collection("institutions")
    private fun institution(instId: String) = institutions().document(instId)
    private fun members(instId: String) = institution(instId).collection("members")
    private fun categories(instId: String) = institution(instId).collection("categories")
    private fun entries(instId: String) = institution(instId).collection("entries")
    private fun codes() = db.collection("institutionCodes")
    private fun myMemberships() = db.collection("users").document(uid).collection("memberships")

    // ── Memberships (the switcher's data source) ──

    fun observeMemberships(cb: (List<Membership>) -> Unit): ListenerRegistration =
        myMemberships().addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            cb(snap.documents.map { d ->
                Membership(
                    institutionId = d.id,
                    role = d.getString("role") ?: Role.MEMBER,
                    institutionName = d.getString("institutionName") ?: "",
                    institutionType = d.getString("institutionType") ?: "Temple",
                    currency = d.getString("currency") ?: "INR",
                )
            }.sortedBy { it.institutionName.lowercase() })
        }

    // ── Institution create ──

    /** Sequential create: institution doc -> owner member doc -> memberships index ->
     * code reservation -> seed categories. Each step is awaited; the rules only allow
     * the next step once the previous one has actually committed. */
    suspend fun createInstitution(name: String, type: String, currency: String): Institution {
        val user = auth.currentUser
        val ref = institutions().document()
        val code = generateUniqueCode()

        val instData = hashMapOf(
            "name" to name, "type" to type, "currency" to currency,
            "code" to code, "ownerId" to uid, "createdAt" to FieldValue.serverTimestamp(),
        )
        ref.set(instData).await()

        members(ref.id).document(uid).set(
            hashMapOf(
                "role" to Role.OWNER,
                "displayName" to (user?.displayName ?: ""),
                "email" to (user?.email ?: ""),
                "photoUrl" to (user?.photoUrl?.toString() ?: ""),
                "joinedAt" to FieldValue.serverTimestamp(),
            )
        ).await()

        myMemberships().document(ref.id).set(
            hashMapOf(
                "role" to Role.OWNER, "institutionName" to name,
                "institutionType" to type, "currency" to currency,
            )
        ).await()

        codes().document(code).set(hashMapOf("institutionId" to ref.id, "name" to name)).await()

        // Seed starter categories in a single write batch (SPEC §5).
        val batch = db.batch()
        val seed = Categories.INCOME.map { it to "income" } + Categories.EXPENSE.map { it to "expense" }
        for ((catName, kind) in seed) {
            val docRef = categories(ref.id).document()
            batch.set(docRef, hashMapOf("name" to catName, "kind" to kind, "createdAt" to FieldValue.serverTimestamp()))
        }
        batch.commit().await()

        return Institution(id = ref.id, name = name, type = type, currency = currency, code = code, ownerId = uid)
    }

    private suspend fun generateUniqueCode(): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // no 0/O, 1/I/L
        repeat(8) {
            val code = (1..6).map { alphabet.random() }.joinToString("")
            val existing = codes().document(code).get().await()
            if (!existing.exists()) return code
        }
        error("Could not generate a unique institution code")
    }

    // ── Join by code ──

    data class CodePreview(val code: String, val institutionId: String, val institutionName: String)

    suspend fun resolveCode(code: String): CodePreview? {
        val doc = codes().document(code.uppercase()).get().await()
        if (!doc.exists()) return null
        val instId = doc.getString("institutionId") ?: return null
        val name = doc.getString("name") ?: ""
        return CodePreview(code.uppercase(), instId, name)
    }

    /** Joins as a plain member (SPEC §3: default role is always member), then reads
     * back the full institution doc (now readable, since membership just committed)
     * to populate an accurate memberships index entry. */
    suspend fun joinInstitution(preview: CodePreview): Institution {
        val user = auth.currentUser
        val existing = members(preview.institutionId).document(uid).get().await()
        if (!existing.exists()) {
            members(preview.institutionId).document(uid).set(
                hashMapOf(
                    "role" to Role.MEMBER,
                    "displayName" to (user?.displayName ?: ""),
                    "email" to (user?.email ?: ""),
                    "photoUrl" to (user?.photoUrl?.toString() ?: ""),
                    "joinedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
        val instDoc = institution(preview.institutionId).get().await()
        val inst = mapInstitution(instDoc)
        myMemberships().document(inst.id).set(
            hashMapOf(
                "role" to (if (existing.exists()) existing.getString("role") ?: Role.MEMBER else Role.MEMBER),
                "institutionName" to inst.name, "institutionType" to inst.type, "currency" to inst.currency,
            )
        ).await()
        return inst
    }

    fun joinLink(code: String): String = "https://muthal-web.vercel.app/join/$code"

    /** Owner only (SPEC §3). Permanently removes the institution and everything in it.
     * A sequence of separately-awaited deletes, ordered so every step's rule check
     * still has what it needs — see SPEC §2 for the exact reasoning per step. */
    suspend fun deleteInstitution(instId: String) = coroutineScope {
        val inst = institution(instId).get().await()
        val code = inst.getString("code")

        val jobs = mutableListOf<Deferred<*>>()

        if (!code.isNullOrEmpty()) {
            jobs.add(async { codes().document(code).delete().await() })
        }

        for (d in entries(instId).get().await().documents) {
            jobs.add(async { d.reference.delete().await() })
        }
        for (d in categories(instId).get().await().documents) {
            jobs.add(async { d.reference.delete().await() })
        }

        for (m in members(instId).get().await().documents) {
            if (m.id == uid) continue
            jobs.add(async {
                try {
                    db.collection("users").document(m.id).collection("memberships").document(instId)
                        .delete().await()
                } catch (e: Exception) {
                    // Ignore index deletion failures if the user doesn't exist
                }
            })
            jobs.add(async { m.reference.delete().await() })
        }

        jobs.awaitAll()

        myMemberships().document(instId).delete().await()
        institution(instId).delete().await()
        members(instId).document(uid).delete().await()
    }

    // ── Institution detail / members ──

    fun observeInstitution(instId: String, cb: (Institution?) -> Unit): ListenerRegistration =
        institution(instId).addSnapshotListener { d, _ -> cb(d?.let { if (it.exists()) mapInstitution(it) else null }) }

    private fun mapInstitution(d: DocumentSnapshot) = Institution(
        id = d.id,
        name = d.getString("name") ?: "",
        type = d.getString("type") ?: "Temple",
        currency = d.getString("currency") ?: "INR",
        code = d.getString("code") ?: "",
        ownerId = d.getString("ownerId") ?: "",
        createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0,
    )

    fun observeMembers(instId: String, cb: (List<Member>) -> Unit): ListenerRegistration =
        members(instId).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            cb(snap.documents.map { d ->
                Member(
                    uid = d.id,
                    role = d.getString("role") ?: Role.MEMBER,
                    displayName = d.getString("displayName") ?: "",
                    email = d.getString("email") ?: "",
                    photoUrl = d.getString("photoUrl") ?: "",
                    joinedAt = d.getTimestamp("joinedAt")?.toDate()?.time ?: 0,
                )
            }.sortedWith(compareBy({ it.role != Role.OWNER }, { it.role != Role.ADMIN }, { it.displayName.lowercase() })))
        }

    /** Owner only (enforced server-side); also fans the role out to the member's own
     * memberships index so their switcher reflects the change immediately. */
    suspend fun setMemberRole(instId: String, memberUid: String, role: String) {
        members(instId).document(memberUid).update("role", role).await()
        db.collection("users").document(memberUid).collection("memberships").document(instId)
            .update("role", role).await()
    }

    /** Owner removes a member, or a member leaves on their own. */
    suspend fun removeMember(instId: String, memberUid: String) {
        members(instId).document(memberUid).delete().await()
        db.collection("users").document(memberUid).collection("memberships").document(instId)
            .delete().await()
    }

    // ── Categories (admin/owner write; SPEC §5) ──

    fun observeCategories(instId: String, cb: (List<Category>) -> Unit): ListenerRegistration =
        categories(instId).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            cb(snap.documents.map { d ->
                Category(id = d.id, name = d.getString("name") ?: "", kind = d.getString("kind") ?: "income")
            }.sortedWith(compareBy({ it.kind }, { it.name.lowercase() })))
        }

    fun addCategory(instId: String, name: String, kind: String) {
        categories(instId).document().set(
            hashMapOf("name" to name, "kind" to kind, "createdAt" to FieldValue.serverTimestamp())
        )
    }

    fun deleteCategory(instId: String, categoryId: String) {
        categories(instId).document(categoryId).delete()
    }

    // ── Entries (admin/owner write, all members read; SPEC §2) ──

    fun observeEntries(instId: String, cb: (List<Entry>) -> Unit): ListenerRegistration =
        entries(instId).orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                cb(snap.documents.map { mapEntry(it) })
            }

    /** One-shot read, for the period export screen (no need for a live listener there). */
    suspend fun getEntriesOnce(instId: String): List<Entry> =
        entries(instId).orderBy("date", Query.Direction.ASCENDING).get().await().documents.map { mapEntry(it) }

    private fun mapEntry(d: DocumentSnapshot): Entry = Entry(
        id = d.id,
        date = d.getTimestamp("date")?.toDate()?.time ?: 0,
        amount = d.getDouble("amount") ?: 0.0,
        type = d.getString("type") ?: "income",
        category = d.getString("category") ?: "",
        note = d.getString("note") ?: "",
        createdBy = d.getString("createdBy") ?: "",
        createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0,
    )

    fun saveEntry(
        instId: String,
        existingId: String?,
        dateMillis: Long,
        amount: Double,
        type: String,
        category: String,
        note: String,
    ) {
        val id = existingId ?: entries(instId).document().id
        val payload = hashMapOf<String, Any>(
            "date" to Timestamp(Date(dateMillis)),
            "amount" to amount,
            "type" to type,
            "category" to category,
            "note" to note,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        entries(instId).document(id).set(payload)
    }

    fun deleteEntry(instId: String, id: String) {
        entries(instId).document(id).delete()
    }
}
