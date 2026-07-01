import Foundation
import FirebaseFirestore

/// Firestore data layer. Entries are dual-written to the nested authoritative path
/// and the flat mirror with the same id (SPEC §2). Mirrors web lib/firestore.ts.
@MainActor
final class FirestoreService: ObservableObject {
    @Published var institutions: [Institution] = []
    @Published var entries: [Entry] = []

    private let db = Firestore.firestore()
    private var instListener: ListenerRegistration?
    private var entryListener: ListenerRegistration?
    private var uid: String?

    func start(uid: String) {
        guard self.uid != uid else { return }
        self.uid = uid
        instListener = db.collection("users").document(uid).collection("institutions")
            .order(by: "createdAt")
            .addSnapshotListener { [weak self] snap, _ in
                guard let docs = snap?.documents else { return }
                self?.institutions = docs.map { d in
                    Institution(
                        id: d.documentID,
                        name: d["name"] as? String ?? "",
                        type: d["type"] as? String ?? "Temple",
                        currency: d["currency"] as? String ?? "INR",
                        createdAt: (d["createdAt"] as? Timestamp)?.dateValue() ?? Date()
                    )
                }
            }
        entryListener = db.collection("users").document(uid).collection("entries")
            .order(by: "date", descending: true)
            .addSnapshotListener { [weak self] snap, _ in
                guard let self, let docs = snap?.documents else { return }
                self.entries = docs.map { self.mapEntry($0, uid: uid) }
            }
    }

    func stop() {
        instListener?.remove(); entryListener?.remove()
        uid = nil; institutions = []; entries = []
    }

    private func mapEntry(_ d: QueryDocumentSnapshot, uid: String) -> Entry {
        Entry(
            id: d.documentID,
            date: (d["date"] as? Timestamp)?.dateValue() ?? Date(),
            amount: d["amount"] as? Double ?? 0,
            type: d["type"] as? String ?? "income",
            category: d["category"] as? String ?? "",
            note: d["note"] as? String ?? "",
            institutionId: d["institutionId"] as? String ?? "",
            institutionName: d["institutionName"] as? String ?? "",
            currency: d["currency"] as? String ?? "INR",
            userId: d["userId"] as? String ?? uid
        )
    }

    func addInstitution(name: String, type: String, currency: String) {
        guard let uid else { return }
        db.collection("users").document(uid).collection("institutions").document()
            .setData([
                "name": name, "type": type, "currency": currency,
                "createdAt": FieldValue.serverTimestamp(),
            ])
    }

    func saveEntry(inst: Institution, existingId: String?, date: Date, amount: Double,
                   type: String, category: String, note: String) {
        guard let uid else { return }
        let mirror = db.collection("users").document(uid).collection("entries")
        let id = existingId ?? mirror.document().documentID
        let payload: [String: Any] = [
            "date": Timestamp(date: date), "amount": amount, "type": type,
            "category": category, "note": note, "institutionId": inst.id,
            "institutionName": inst.name, "currency": inst.currency, "userId": uid,
        ]
        let nested = db.collection("users").document(uid).collection("institutions")
            .document(inst.id).collection("entries").document(id)
        let batch = db.batch()
        batch.setData(payload, forDocument: nested)
        batch.setData(payload, forDocument: mirror.document(id))
        batch.commit()
    }

    func deleteEntry(instId: String, id: String) {
        guard let uid else { return }
        let nested = db.collection("users").document(uid).collection("institutions")
            .document(instId).collection("entries").document(id)
        let batch = db.batch()
        batch.deleteDocument(nested)
        batch.deleteDocument(db.collection("users").document(uid).collection("entries").document(id))
        batch.commit()
    }
}
