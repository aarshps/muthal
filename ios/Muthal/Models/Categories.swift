import Foundation

/// Ports web lib/categories.ts / android Categories.kt — keep in sync (SPEC §4).
enum Categories {
    static let income = ["Donation", "Offering", "Membership", "Grant", "Other"]
    static let expense = ["Salary", "Utilities", "Maintenance", "Supplies", "Event", "Other"]

    static func forType(_ type: String) -> [String] {
        switch type {
        case "income": return income
        case "expense": return expense
        default: return []
        }
    }
}

let institutionTypes = ["Temple", "Church", "Library", "Other"]
