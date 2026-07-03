import SwiftUI

/// Admin/owner-only category CRUD, scoped to one institution (SPEC §5).
struct CategoriesView: View {
    @EnvironmentObject var store: FirestoreService
    @Environment(\.dismiss) private var dismiss

    let institution: Institution

    @State private var showAdd = false
    @State private var newName = ""
    @State private var newKind = "income"

    private var income: [Category] { store.categories.filter { $0.kind == "income" } }
    private var expense: [Category] { store.categories.filter { $0.kind == "expense" } }

    var body: some View {
        NavigationStack {
            List {
                Section("Income") {
                    if income.isEmpty { Text("No categories yet.").foregroundStyle(.secondary) }
                    ForEach(income) { c in row(c) }
                }
                Section("Expense") {
                    if expense.isEmpty { Text("No categories yet.").foregroundStyle(.secondary) }
                    ForEach(expense) { c in row(c) }
                }
            }
            .navigationTitle("Categories")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showAdd = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showAdd) { addSheet }
        }
    }

    private func row(_ c: Category) -> some View {
        HStack {
            Text(c.name)
            Spacer()
            Button(role: .destructive) {
                store.deleteCategory(instId: institution.id, categoryId: c.id)
            } label: {
                Image(systemName: "trash").foregroundStyle(.red)
            }
        }
    }

    private var addSheet: some View {
        NavigationStack {
            Form {
                Picker("Kind", selection: $newKind) {
                    Text("Income").tag("income")
                    Text("Expense").tag("expense")
                }
                .pickerStyle(.segmented)
                TextField("Category name", text: $newName)
            }
            .navigationTitle("New category")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        let n = newName.trimmingCharacters(in: .whitespaces)
                        guard !n.isEmpty else { return }
                        store.addCategory(instId: institution.id, name: n, kind: newKind)
                        newName = ""; newKind = "income"
                        showAdd = false
                    }
                }
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { showAdd = false } }
            }
        }
    }
}
