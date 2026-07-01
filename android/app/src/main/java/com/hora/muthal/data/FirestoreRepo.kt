package com.hora.muthal.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.hora.muthal.model.Entry
import com.hora.muthal.model.Institution
import java.util.Date

/**
 * Firestore data layer. Entries are dual-written to the nested authoritative path
 * and the flat mirror with the same id (SPEC §2). Reads come from the flat mirror
 * ordered by date; the home filters by institution. Mirrors web lib/firestore.ts.
 */
class FirestoreRepo(private val uid: String) {
    private val db = FirebaseFirestore.getInstance()

    private fun instCol() = db.collection("users").document(uid).collection("institutions")
    private fun instEntries(instId: String) = instCol().document(instId).collection("entries")
    private fun mirror() = db.collection("users").document(uid).collection("entries")

    fun observeInstitutions(cb: (List<Institution>) -> Unit): ListenerRegistration =
        instCol().orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                cb(snap.documents.map { d ->
                    Institution(
                        id = d.id,
                        name = d.getString("name") ?: "",
                        type = d.getString("type") ?: "Temple",
                        currency = d.getString("currency") ?: "INR",
                        createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0,
                    )
                })
            }

    fun addInstitution(name: String, type: String, currency: String, onDone: (String) -> Unit) {
        val ref = instCol().document()
        val data = hashMapOf(
            "name" to name,
            "type" to type,
            "currency" to currency,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        ref.set(data).addOnSuccessListener { onDone(ref.id) }
    }

    fun observeEntries(cb: (List<Entry>) -> Unit): ListenerRegistration =
        mirror().orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                cb(snap.documents.map { mapEntry(it) })
            }

    private fun mapEntry(d: DocumentSnapshot): Entry = Entry(
        id = d.id,
        date = d.getTimestamp("date")?.toDate()?.time ?: 0,
        amount = d.getDouble("amount") ?: 0.0,
        type = d.getString("type") ?: "income",
        category = d.getString("category") ?: "",
        note = d.getString("note") ?: "",
        institutionId = d.getString("institutionId") ?: "",
        institutionName = d.getString("institutionName") ?: "",
        currency = d.getString("currency") ?: "INR",
        userId = d.getString("userId") ?: uid,
    )

    fun saveEntry(
        inst: Institution,
        existingId: String?,
        dateMillis: Long,
        amount: Double,
        type: String,
        category: String,
        note: String,
    ) {
        val id = existingId ?: mirror().document().id
        val payload = hashMapOf(
            "date" to Timestamp(Date(dateMillis)),
            "amount" to amount,
            "type" to type,
            "category" to category,
            "note" to note,
            "institutionId" to inst.id,
            "institutionName" to inst.name,
            "currency" to inst.currency,
            "userId" to uid,
        )
        db.runBatch { b ->
            b.set(instEntries(inst.id).document(id), payload)
            b.set(mirror().document(id), payload)
        }
    }

    fun deleteEntry(instId: String, id: String) {
        db.runBatch { b ->
            b.delete(instEntries(instId).document(id))
            b.delete(mirror().document(id))
        }
    }
}
