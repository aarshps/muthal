import Foundation

/// Canonical models — port web lib/types.ts / android model/*.kt (SPEC §1). Multi-user
/// institutions with roles: see SPEC §3 for the role matrix.
struct Institution: Identifiable, Hashable {
    var id: String
    var name: String
    var type: String
    var currency: String
    var code: String
    var ownerId: String
    var createdAt: Date
}

enum Role: String {
    case owner, admin, member

    var isAdminOrOwner: Bool { self == .owner || self == .admin }
    var label: String {
        switch self {
        case .owner: return "Owner"
        case .admin: return "Admin"
        case .member: return "Member"
        }
    }
}

/// A user's membership + role within one institution (institutions/{id}/members/{uid}).
struct Member: Identifiable, Hashable {
    var id: String { uid }
    var uid: String
    var role: Role
    var displayName: String
    var email: String
    var photoUrl: String
    var joinedAt: Date
}

/// A user's index entry for one institution they belong to
/// (users/{uid}/memberships/{id}), with denormalized institution data for the switcher.
struct Membership: Identifiable, Hashable {
    var id: String { institutionId }
    var institutionId: String
    var role: Role
    var institutionName: String
    var institutionType: String
    var currency: String
}

enum EntryType: String { case income, expense }

struct Category: Identifiable, Hashable {
    var id: String
    var name: String
    var kind: String // "income" | "expense"
}

struct Entry: Identifiable, Hashable {
    var id: String
    var date: Date
    var amount: Double
    var type: String
    var category: String
    var note: String
    var createdBy: String
    var createdAt: Date
}
