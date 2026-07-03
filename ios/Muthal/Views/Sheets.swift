import SwiftUI

struct EntrySheet: View {
    @EnvironmentObject var store: FirestoreService
    @Environment(\.dismiss) private var dismiss

    let institution: Institution
    let entry: Entry?

    @State private var type = "income"
    @State private var amount = ""
    @State private var category = ""
    @State private var date = Date()
    @State private var note = ""

    private func categoriesFor(_ t: String) -> [String] {
        store.categories.filter { $0.kind == t }.map { $0.name }
    }

    var body: some View {
        NavigationStack {
            Form {
                Picker("Type", selection: $type) {
                    Text("Income").tag("income")
                    Text("Expense").tag("expense")
                }
                .pickerStyle(.segmented)
                .onChange(of: type) { _, newType in
                    if !categoriesFor(newType).contains(category) {
                        category = categoriesFor(newType).first ?? ""
                    }
                }
                TextField("Amount (\(institution.currency))", text: $amount)
                    .keyboardType(.decimalPad)
                let options = categoriesFor(type)
                if options.isEmpty {
                    Text("No \(type) categories yet — add one from Institution > Categories.")
                        .foregroundStyle(.secondary)
                } else {
                    Picker("Category", selection: $category) {
                        ForEach(options, id: \.self) { Text($0).tag($0) }
                    }
                }
                // Defaults to today at open; user-editable (SPEC §1).
                DatePicker("Date", selection: $date, displayedComponents: .date)
                TextField("Note (optional)", text: $note)
                if entry != nil {
                    Button("Delete", role: .destructive) {
                        if let e = entry { store.deleteEntry(instId: institution.id, id: e.id) }
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
            category = categoriesFor(type).first ?? ""
        }
    }

    private func save() {
        guard let value = Double(amount), value > 0, !category.isEmpty else { return }
        store.saveEntry(
            instId: institution.id, existingId: entry?.id, date: date, amount: value,
            type: type, category: category,
            note: note.trimmingCharacters(in: .whitespaces)
        )
        dismiss()
    }
}

struct InstitutionSheet: View {
    @EnvironmentObject var auth: AuthService
    @EnvironmentObject var store: FirestoreService
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var type = institutionTypes[0]
    @State private var currency = "INR"
    @State private var busy = false
    @State private var error: String?

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
                if let error { Text(error).foregroundStyle(.red).font(.caption) }
            }
            .navigationTitle("New institution")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Creating…" : "Create") { Task { await create() } }
                        .disabled(busy)
                }
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
    }

    private func create() async {
        let n = name.trimmingCharacters(in: .whitespaces)
        guard !n.isEmpty else { error = "Give the institution a name."; return }
        busy = true
        defer { busy = false }
        do {
            let inst = try await store.createInstitution(
                name: n, type: type, currency: currency,
                profile: (auth.user?.displayName ?? "", auth.user?.email ?? "", auth.user?.photoURL?.absoluteString ?? "")
            )
            store.selectInstitution(inst.id)
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}

/// Enter a 6-character join code, resolve it to a preview, confirm, then join (SPEC §2).
struct JoinSheet: View {
    @EnvironmentObject var auth: AuthService
    @EnvironmentObject var store: FirestoreService
    @Environment(\.dismiss) private var dismiss

    var prefillCode: String?

    @State private var code = ""
    @State private var preview: FirestoreService.CodePreview?
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                if let preview {
                    Text("Join \"\(preview.institutionName)\"? You'll be added as a member.")
                } else {
                    TextField("Code", text: $code)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .onChange(of: code) { _, v in code = String(v.uppercased().prefix(6)) }
                }
                if let error { Text(error).foregroundStyle(.red).font(.caption) }
            }
            .navigationTitle("Join institution")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Working…" : (preview == nil ? "Join" : "Confirm")) {
                        Task {
                            if preview == nil { await resolve() } else { await confirmJoin() }
                        }
                    }
                    .disabled(busy)
                }
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
            .onAppear {
                if let prefillCode, prefillCode.count == 6 {
                    code = prefillCode
                    Task { await resolve() }
                }
            }
        }
    }

    private func resolve() async {
        guard code.count == 6 else { error = "Enter the 6-character code."; return }
        busy = true
        defer { busy = false }
        do {
            guard let result = try await store.resolveCode(code) else {
                error = "Invalid or expired code."
                return
            }
            preview = result
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func confirmJoin() async {
        guard let preview else { return }
        busy = true
        defer { busy = false }
        do {
            let inst = try await store.joinInstitution(
                preview,
                profile: (auth.user?.displayName ?? "", auth.user?.email ?? "", auth.user?.photoURL?.absoluteString ?? "")
            )
            store.selectInstitution(inst.id)
            store.pendingJoinCode = nil
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
