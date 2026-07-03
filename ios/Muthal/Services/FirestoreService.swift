import Foundation
import FirebaseFirestore

/// Firestore data layer for the v2 multi-user institution model (SPEC §1-3). Institutions
/// are top-level documents shared by members with a role each; join is by a 6-character
/// code. Institution creation and joining are SEQUENCES of awaited writes (not a batch) —
/// see the security-rules comments in shared/firebase/firestore.rules for why (rules
/// can't see sibling writes within the same batch). Mirrors android FirestoreRepo.kt /
/// web lib/firestore.ts.
@MainActor
final class FirestoreService: ObservableObject {
    @Published var memberships: [Membership] = []
    @Published var institution: Institution?
    @Published var categories: [Category] = []
    @Published var entries: [Entry] = []
    @Published var pendingJoinCode: String?

    private let db = Firestore.firestore()
    private var membershipsListener: ListenerRegistration?
    private var instListener: ListenerRegistration?
    private var categoriesListener: ListenerRegistration?
    private var entriesListener: ListenerRegistration?
    private var uid: String?
    private var currentInstId: String?

    private func institutionsCol() -> CollectionReference { db.collection("institutions") }
    private func membersCol(_ instId: String) -> CollectionReference {
        institutionsCol().document(instId).collection("members")
    }
    private func categoriesCol(_ instId: String) -> CollectionReference {
        institutionsCol().document(instId).collection("categories")
    }
    private func entriesCol(_ instId: String) -> CollectionReference {
        institutionsCol().document(instId).collection("entries")
    }
    private func codesCol() -> CollectionReference { db.collection("institutionCodes") }
    private func myMembershipsCol(_ uid: String) -> CollectionReference {
        db.collection("users").document(uid).collection("memberships")
    }

    // MARK: memberships

    func start(uid: String) {
        guard self.uid != uid else { return }
        self.uid = uid
        membershipsListener = myMembershipsCol(uid).addSnapshotListener { [weak self] snap, _ in
            guard let docs = snap?.documents else { return }
            self?.memberships = docs.map { d in
                Membership(
                    institutionId: d.documentID,
                    role: Role(rawValue: d["role"] as? String ?? "member") ?? .member,
                    institutionName: d["institutionName"] as? String ?? "",
                    institutionType: d["institutionType"] as? String ?? "Temple",
                    currency: d["currency"] as? String ?? "INR"
                )
            }.sorted { $0.institutionName.localizedCaseInsensitiveCompare($1.institutionName) == .orderedAscending }
        }
    }

    func stop() {
        membershipsListener?.remove(); detachInstitutionListeners()
        uid = nil; memberships = []; institution = nil; categories = []; entries = []
    }

    /// Live institution doc + its categories + entries, for the currently selected institution.
    func selectInstitution(_ instId: String) {
        guard currentInstId != instId else { return }
        detachInstitutionListeners()
        currentInstId = instId
        institution = nil; categories = []; entries = []

        instListener = institutionsCol().document(instId).addSnapshotListener { [weak self] d, _ in
            guard let d, d.exists else { return }
            self?.institution = Self.mapInstitution(d)
        }
        categoriesListener = categoriesCol(instId).addSnapshotListener { [weak self] snap, _ in
            guard let docs = snap?.documents else { return }
            self?.categories = docs.map { d in
                Category(id: d.documentID, name: d["name"] as? String ?? "", kind: d["kind"] as? String ?? "income")
            }.sorted { ($0.kind, $0.name) < ($1.kind, $1.name) }
        }
        entriesListener = entriesCol(instId).order(by: "date", descending: true)
            .addSnapshotListener { [weak self] snap, _ in
                guard let docs = snap?.documents else { return }
                self?.entries = docs.map(Self.mapEntry)
            }
    }

    private func detachInstitutionListeners() {
        instListener?.remove(); categoriesListener?.remove(); entriesListener?.remove()
        instListener = nil; categoriesListener = nil; entriesListener = nil
        currentInstId = nil
    }

    private static func mapInstitution(_ d: DocumentSnapshot) -> Institution {
        Institution(
            id: d.documentID,
            name: d["name"] as? String ?? "",
            type: d["type"] as? String ?? "Temple",
            currency: d["currency"] as? String ?? "INR",
            code: d["code"] as? String ?? "",
            ownerId: d["ownerId"] as? String ?? "",
            createdAt: (d["createdAt"] as? Timestamp)?.dateValue() ?? Date()
        )
    }

    private static func mapEntry(_ d: QueryDocumentSnapshot) -> Entry {
        Entry(
            id: d.documentID,
            date: (d["date"] as? Timestamp)?.dateValue() ?? Date(),
            amount: d["amount"] as? Double ?? 0,
            type: d["type"] as? String ?? "income",
            category: d["category"] as? String ?? "",
            note: d["note"] as? String ?? "",
            createdBy: d["createdBy"] as? String ?? "",
            createdAt: (d["createdAt"] as? Timestamp)?.dateValue() ?? Date()
        )
    }

    // MARK: institution create

    private static let codeAlphabet = Array("ABCDEFGHJKMNPQRSTUVWXYZ23456789") // no 0/O, 1/I/L

    private func generateUniqueCode() async throws -> String {
        for _ in 0..<8 {
            let code = String((0..<6).map { _ in Self.codeAlphabet.randomElement()! })
            let existing = try await codesCol().document(code).getDocument()
            if !existing.exists { return code }
        }
        throw NSError(domain: "Muthal", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not generate a unique institution code"])
    }

    /// Sequential create: institution doc -> owner member doc -> memberships index ->
    /// code reservation -> seed categories. Each step is awaited; the rules only allow
    /// the next step once the previous one has actually committed.
    func createInstitution(
        name: String, type: String, currency: String,
        profile: (displayName: String, email: String, photoUrl: String)
    ) async throws -> Institution {
        guard let uid else { throw NSError(domain: "Muthal", code: 0) }
        let ref = institutionsCol().document()
        let code = try await generateUniqueCode()

        try await ref.setData([
            "name": name, "type": type, "currency": currency,
            "code": code, "ownerId": uid, "createdAt": FieldValue.serverTimestamp(),
        ])

        try await membersCol(ref.documentID).document(uid).setData([
            "role": Role.owner.rawValue, "displayName": profile.displayName,
            "email": profile.email, "photoUrl": profile.photoUrl,
            "joinedAt": FieldValue.serverTimestamp(),
        ])

        try await myMembershipsCol(uid).document(ref.documentID).setData([
            "role": Role.owner.rawValue, "institutionName": name,
            "institutionType": type, "currency": currency,
        ])

        try await codesCol().document(code).setData(["institutionId": ref.documentID, "name": name])

        let seed: [(String, String)] = Categories.income.map { ($0, "income") } + Categories.expense.map { ($0, "expense") }
        for (catName, kind) in seed {
            try await categoriesCol(ref.documentID).document().setData([
                "name": catName, "kind": kind, "createdAt": FieldValue.serverTimestamp(),
            ])
        }

        return Institution(id: ref.documentID, name: name, type: type, currency: currency, code: code, ownerId: uid, createdAt: Date())
    }

    // MARK: join by code

    struct CodePreview {
        let code: String
        let institutionId: String
        let institutionName: String
    }

    func resolveCode(_ code: String) async throws -> CodePreview? {
        let doc = try await codesCol().document(code.uppercased()).getDocument()
        guard doc.exists, let instId = doc["institutionId"] as? String else { return nil }
        return CodePreview(code: code.uppercased(), institutionId: instId, institutionName: doc["name"] as? String ?? "")
    }

    /// Joins as a plain member (SPEC §3: default role is always member), then reads back
    /// the full institution doc (now readable, since membership just committed) to
    /// populate an accurate memberships index entry.
    func joinInstitution(
        _ preview: CodePreview,
        profile: (displayName: String, email: String, photoUrl: String)
    ) async throws -> Institution {
        guard let uid else { throw NSError(domain: "Muthal", code: 0) }
        let existing = try await membersCol(preview.institutionId).document(uid).getDocument()
        if !existing.exists {
            try await membersCol(preview.institutionId).document(uid).setData([
                "role": Role.member.rawValue, "displayName": profile.displayName,
                "email": profile.email, "photoUrl": profile.photoUrl,
                "joinedAt": FieldValue.serverTimestamp(),
            ])
        }
        let instDoc = try await institutionsCol().document(preview.institutionId).getDocument()
        let inst = Self.mapInstitution(instDoc)
        let role = existing.exists ? (existing["role"] as? String ?? "member") : "member"
        try await myMembershipsCol(uid).document(inst.id).setData([
            "role": role, "institutionName": inst.name, "institutionType": inst.type, "currency": inst.currency,
        ])
        return inst
    }

    func joinLink(_ code: String) -> String { "https://muthal-web.vercel.app/join/\(code)" }

    /// Owner only (SPEC §3). Permanently removes the institution and everything in it.
    /// A sequence of separately-awaited deletes, ordered so every step's rule check
    /// still has what it needs — see SPEC §2 for the exact reasoning per step.
    func deleteInstitution(instId: String) async throws {
        guard let uid else { return }
        let instDoc = try await institutionsCol().document(instId).getDocument()
        let code = instDoc["code"] as? String

        if let code, !code.isEmpty {
            try await codesCol().document(code).delete()
        }

        for d in try await entriesCol(instId).getDocuments().documents { try await d.reference.delete() }
        for d in try await categoriesCol(instId).getDocuments().documents { try await d.reference.delete() }

        for m in try await membersCol(instId).getDocuments().documents {
            if m.documentID == uid { continue }
            try? await db.collection("users").document(m.documentID).collection("memberships")
                .document(instId).delete()
            try await m.reference.delete()
        }

        try await myMembershipsCol(uid).document(instId).delete()
        try await institutionsCol().document(instId).delete()
        try await membersCol(instId).document(uid).delete()
    }

    // MARK: members

    /// Owner only (enforced server-side); also fans the role out to the member's own
    /// memberships index so their switcher reflects the change immediately.
    func setMemberRole(instId: String, memberUid: String, role: Role) async throws {
        try await membersCol(instId).document(memberUid).updateData(["role": role.rawValue])
        try await db.collection("users").document(memberUid).collection("memberships")
            .document(instId).updateData(["role": role.rawValue])
    }

    /// Owner removes a member, or a member leaves on their own.
    func removeMember(instId: String, memberUid: String) async throws {
        try await membersCol(instId).document(memberUid).delete()
        try? await db.collection("users").document(memberUid).collection("memberships")
            .document(instId).delete()
    }

    func fetchMembers(instId: String) async throws -> [Member] {
        let snap = try await membersCol(instId).getDocuments()
        return snap.documents.map { d in
            Member(
                uid: d.documentID,
                role: Role(rawValue: d["role"] as? String ?? "member") ?? .member,
                displayName: d["displayName"] as? String ?? "",
                email: d["email"] as? String ?? "",
                photoUrl: d["photoUrl"] as? String ?? "",
                joinedAt: (d["joinedAt"] as? Timestamp)?.dateValue() ?? Date()
            )
        }.sorted {
            if $0.role != $1.role { return $0.role == .owner || ($0.role == .admin && $1.role == .member) }
            return $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending
        }
    }

    // MARK: categories

    func addCategory(instId: String, name: String, kind: String) {
        categoriesCol(instId).document().setData(["name": name, "kind": kind, "createdAt": FieldValue.serverTimestamp()])
    }

    func deleteCategory(instId: String, categoryId: String) {
        categoriesCol(instId).document(categoryId).delete()
    }

    // MARK: entries

    func saveEntry(instId: String, existingId: String?, date: Date, amount: Double,
                   type: String, category: String, note: String) {
        guard let uid else { return }
        let id = existingId ?? entriesCol(instId).document().documentID
        entriesCol(instId).document(id).setData([
            "date": Timestamp(date: date), "amount": amount, "type": type,
            "category": category, "note": note, "createdBy": uid,
            "createdAt": FieldValue.serverTimestamp(),
        ])
    }

    func deleteEntry(instId: String, id: String) {
        entriesCol(instId).document(id).delete()
    }

    /// One-shot read, for the period export screen (no need for a live listener there).
    func fetchEntriesOnce(instId: String) async throws -> [Entry] {
        let snap = try await entriesCol(instId).order(by: "date", descending: false).getDocuments()
        return snap.documents.map(Self.mapEntry)
    }

    /// Settings → Delete account: removes only the user's OWN presence (their member
    /// doc in each institution + their memberships index), never shared entries/
    /// categories other members depend on. If the user was the sole owner of an
    /// institution, it is left ownerless per SPEC §3 (acceptable at this scale).
    func deleteAllUserData(uid: String) async throws {
        let memberships = try await myMembershipsCol(uid).getDocuments()
        for m in memberships.documents {
            try? await membersCol(m.documentID).document(uid).delete()
            try await m.reference.delete()
        }
    }
}
