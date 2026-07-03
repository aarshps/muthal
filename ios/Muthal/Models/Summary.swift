import Foundation

/// Ports web lib/summary.ts / android SummaryHelper.kt — keep in sync (SPEC §5).
struct Summary {
    let totalIncome: Double
    let totalExpense: Double
    let balance: Double
    let monthIncome: Double
    let monthExpense: Double
    let monthBalance: Double
}

enum SummaryCalc {
    struct Item {
        let amount: Double
        let type: String
        let date: Date
    }

    /// Month membership decided by the UTC calendar month+year of `now`.
    static func summarize(_ entries: [Item], now: Date) -> Summary {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let ny = cal.component(.year, from: now)
        let nm = cal.component(.month, from: now)

        var ti = 0.0, te = 0.0, mi = 0.0, me = 0.0
        for e in entries {
            let inMonth = cal.component(.year, from: e.date) == ny
                && cal.component(.month, from: e.date) == nm
            if e.type == "income" {
                ti += e.amount; if inMonth { mi += e.amount }
            } else if e.type == "expense" {
                te += e.amount; if inMonth { me += e.amount }
            }
        }
        return Summary(
            totalIncome: ti, totalExpense: te, balance: ti - te,
            monthIncome: mi, monthExpense: me, monthBalance: mi - me
        )
    }

    /// Opening/closing balance + in-range totals for [start, endInclusive] (SPEC §7).
    /// Entries strictly before the window feed only the opening balance; entries
    /// strictly after the window are excluded entirely.
    static func periodSummarize(_ entries: [Item], start: Date, endInclusive: Date) -> PeriodSummary {
        var opening = 0.0, periodIncome = 0.0, periodExpense = 0.0, count = 0
        for e in entries {
            if e.date < start {
                if e.type == "income" { opening += e.amount }
                else if e.type == "expense" { opening -= e.amount }
            } else if e.date >= start && e.date <= endInclusive {
                count += 1
                if e.type == "income" { periodIncome += e.amount }
                else if e.type == "expense" { periodExpense += e.amount }
            }
        }
        return PeriodSummary(
            openingBalance: opening, periodIncome: periodIncome, periodExpense: periodExpense,
            closingBalance: opening + periodIncome - periodExpense, entryCount: count
        )
    }
}

struct PeriodSummary {
    let openingBalance: Double
    let periodIncome: Double
    let periodExpense: Double
    let closingBalance: Double
    let entryCount: Int
}
