import Foundation

/// Canonical models — port web lib/types.ts / android model (SPEC §1).
struct Institution: Identifiable, Hashable {
    var id: String
    var name: String
    var type: String
    var currency: String
    var createdAt: Date
}

enum EntryType: String { case income, expense }

struct Entry: Identifiable, Hashable {
    var id: String
    var date: Date
    var amount: Double
    var type: String
    var category: String
    var note: String
    var institutionId: String
    var institutionName: String
    var currency: String
    var userId: String
}
