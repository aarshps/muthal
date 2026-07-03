import SwiftUI
import UIKit

/// Start/end date range → opening balance, in-range entries, closing balance (SPEC §7).
struct PeriodExportView: View {
    @EnvironmentObject var store: FirestoreService
    @Environment(\.dismiss) private var dismiss

    let institution: Institution

    @State private var start = Calendar(identifier: .gregorian).date(
        from: Calendar(identifier: .gregorian).dateComponents([.year, .month], from: Date())
    ) ?? Date()
    @State private var end = Date()
    @State private var busy = false
    @State private var error: String?
    @State private var result: PeriodSummary?
    @State private var inRange: [Entry] = []

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("Start date", selection: $start, displayedComponents: .date)
                    DatePicker("End date", selection: $end, displayedComponents: .date)
                    Button(busy ? "Running…" : "Run export") { Task { await run() } }
                        .disabled(busy)
                    if let error { Text(error).foregroundStyle(.red).font(.caption) }
                }

                if let result {
                    Section {
                        HStack {
                            VStack(alignment: .leading) {
                                Text("Opening balance").font(.caption).foregroundStyle(.secondary)
                                Text(Currency.format(result.openingBalance, institution.currency)).font(.title3.bold())
                            }
                            Spacer()
                            VStack(alignment: .trailing) {
                                Text("Closing balance").font(.caption).foregroundStyle(.secondary)
                                Text(Currency.format(result.closingBalance, institution.currency)).font(.title3.bold())
                            }
                        }
                        Text("+\(Currency.format(result.periodIncome, institution.currency)) / −\(Currency.format(result.periodExpense, institution.currency)) · \(result.entryCount) \(result.entryCount == 1 ? "entry" : "entries")")
                            .font(.caption).foregroundStyle(.secondary)
                    }

                    Section {
                        ForEach(inRange) { e in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(e.category.isEmpty ? "Uncategorized" : e.category).fontWeight(.semibold)
                                    Text(e.date, style: .date).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text((e.type == "income" ? "+" : "−") + Currency.format(e.amount, institution.currency))
                                    .fontWeight(.bold)
                                    .foregroundStyle(e.type == "income" ? Color.accentColor : Color.red)
                            }
                        }
                    }

                    Section {
                        Button { share() } label: { Label("Share", systemImage: "square.and.arrow.up") }
                    }
                }
            }
            .navigationTitle("Period export")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
            }
        }
    }

    private func run() async {
        let cal = Calendar(identifier: .gregorian)
        let endInclusive = cal.date(bySettingHour: 23, minute: 59, second: 59, of: end) ?? end
        guard endInclusive >= start else { error = "End date is before start date."; return }
        busy = true
        defer { busy = false }
        do {
            let all = try await store.fetchEntriesOnce(instId: institution.id)
            let items = all.map { SummaryCalc.Item(amount: $0.amount, type: $0.type, date: $0.date) }
            result = SummaryCalc.periodSummarize(items, start: start, endInclusive: endInclusive)
            inRange = all.filter { $0.date >= start && $0.date <= endInclusive }.sorted { $0.date > $1.date }
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func share() {
        guard let result else { return }
        var lines: [String] = []
        lines.append("\(institution.name) — period export")
        lines.append("")
        lines.append("Opening balance: \(Currency.format(result.openingBalance, institution.currency))")
        for e in inRange.sorted(by: { $0.date < $1.date }) {
            let sign = e.type == "income" ? "+" : "-"
            let df = DateFormatter(); df.dateStyle = .medium
            lines.append("\(df.string(from: e.date))  \(e.category.isEmpty ? "Uncategorized" : e.category)  \(sign)\(Currency.format(e.amount, institution.currency))")
        }
        lines.append("")
        lines.append("Income: +\(Currency.format(result.periodIncome, institution.currency))")
        lines.append("Expense: -\(Currency.format(result.periodExpense, institution.currency))")
        lines.append("Closing balance: \(Currency.format(result.closingBalance, institution.currency))")
        let av = UIActivityViewController(activityItems: [lines.joined(separator: "\n")], applicationActivities: nil)
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?.keyWindow?.rootViewController?.present(av, animated: true)
    }
}
