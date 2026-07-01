import Foundation

/// Ports web lib/currency.ts / android CurrencyHelper.kt — keep in sync (SPEC §3).
struct CurrencyItem: Identifiable, Hashable {
    let code: String
    let name: String
    let symbol: String
    var id: String { code }
}

enum Currency {
    static let all: [CurrencyItem] = [
        CurrencyItem(code: "INR", name: "Indian Rupee", symbol: "₹"),
        CurrencyItem(code: "USD", name: "US Dollar", symbol: "$"),
        CurrencyItem(code: "EUR", name: "Euro", symbol: "€"),
        CurrencyItem(code: "GBP", name: "British Pound", symbol: "£"),
        CurrencyItem(code: "JPY", name: "Japanese Yen", symbol: "¥"),
        CurrencyItem(code: "AUD", name: "Australian Dollar", symbol: "$"),
        CurrencyItem(code: "CAD", name: "Canadian Dollar", symbol: "$"),
        CurrencyItem(code: "CHF", name: "Swiss Franc", symbol: "₣"),
        CurrencyItem(code: "CNY", name: "Chinese Yuan", symbol: "¥"),
        CurrencyItem(code: "HKD", name: "Hong Kong Dollar", symbol: "$"),
        CurrencyItem(code: "NZD", name: "New Zealand Dollar", symbol: "$"),
        CurrencyItem(code: "SEK", name: "Swedish Krona", symbol: "kr"),
        CurrencyItem(code: "KRW", name: "South Korean Won", symbol: "₩"),
        CurrencyItem(code: "SGD", name: "Singapore Dollar", symbol: "$"),
        CurrencyItem(code: "MXN", name: "Mexican Peso", symbol: "$"),
        CurrencyItem(code: "KES", name: "Kenyan Shilling", symbol: "KSh"),
        CurrencyItem(code: "UNT", name: "Generic Unit", symbol: "#"),
    ]

    static func symbol(_ code: String) -> String {
        all.first { $0.code == code }?.symbol ?? "$"
    }

    /// "₹ 649" — integer when whole, else two decimals; sign kept as-is.
    static func format(_ amount: Double, _ code: String) -> String {
        let sym = symbol(code)
        let rounded = amount.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int64(amount))
            : String(format: "%.2f", amount)
        return "\(sym) \(rounded)"
    }

    /// Compact form using k / l (lakh) / m suffixes (Indian-influenced).
    static func compact(_ amount: Double) -> String {
        if amount == 0 { return "0" }
        let a = abs(amount)
        if a >= 1_000_000 { return trim(amount / 1_000_000, "m") }
        if a >= 100_000 { return trim(amount / 100_000, "l") }
        if a >= 1_000 { return trim(amount / 1_000, "k") }
        return String(Int64(amount.rounded()))
    }

    private static func trim(_ value: Double, _ suffix: String) -> String {
        let f = String(format: "%.1f", value)
        return f.hasSuffix(".0") ? String(f.dropLast(2)) + suffix : f + suffix
    }
}
