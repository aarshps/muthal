import SwiftUI

struct EntrySheet: View {
    @EnvironmentObject var store: FirestoreService
    @Environment(\.dismiss) private var dismiss

    let institution: Institution
    let entry: Entry?

    @State private var type = "income"
    @State private var amount = ""
    @State private var category = "Donation"
    @State private var date = Date()
    @State private var note = ""

    var body: some View {
        NavigationStack {
            Form {
                Picker("Type", selection: $type) {
                    Text("Income").tag("income")
                    Text("Expense").tag("expense")
                }
                .pickerStyle(.segmented)
                .onChange(of: type) { _, newType in
                    if !Categories.forType(newType).contains(category) {
                        category = Categories.forType(newType).first ?? ""
                    }
                }
                TextField("Amount (\(institution.currency))", text: $amount)
                    .keyboardType(.decimalPad)
                Picker("Category", selection: $category) {
                    ForEach(Categories.forType(type), id: \.self) { Text($0).tag($0) }
                }
                DatePicker("Date", selection: $date, displayedComponents: .date)
                TextField("Note (optional)", text: $note)
                if entry != nil {
                    Button("Delete", role: .destructive) {
                        if let e = entry { store.deleteEntry(instId: e.institutionId, id: e.id) }
                        dismiss()
                    }
                }
            }
            .navigationTitle(entry == nil ? "Add entry" : "Edit entry")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Save") { save() } }
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
            .onAppear(perform: load)
        }
    }

    private func load() {
        if let e = entry {
            type = e.type
            amount = e.amount.truncatingRemainder(dividingBy: 1) == 0
                ? String(Int64(e.amount)) : String(e.amount)
            category = e.category
            date = e.date
            note = e.note
        } else {
            category = Categories.forType(type).first ?? ""
        }
    }

    private func save() {
        guard let value = Double(amount), value > 0 else { return }
        store.saveEntry(
            inst: institution, existingId: entry?.id, date: date, amount: value,
            type: type, category: category,
            note: note.trimmingCharacters(in: .whitespaces)
        )
        dismiss()
    }
}

struct InstitutionSheet: View {
    @EnvironmentObject var store: FirestoreService
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var type = institutionTypes[0]
    @State private var currency = "INR"

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
                Picker("Type", selection: $type) {
                    ForEach(institutionTypes, id: \.self) { Text($0).tag($0) }
                }
                Picker("Currency", selection: $currency) {
                    ForEach(Currency.all) { Text("\($0.code)  \($0.symbol)").tag($0.code) }
                }
            }
            .navigationTitle("New institution")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        let n = name.trimmingCharacters(in: .whitespaces)
                        if !n.isEmpty {
                            store.addInstitution(name: n, type: type, currency: currency)
                            dismiss()
                        }
                    }
                }
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
    }
}
