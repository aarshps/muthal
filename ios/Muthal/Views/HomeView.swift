import SwiftUI

struct HomeView: View {
    @EnvironmentObject var auth: AuthService
    @EnvironmentObject var store: FirestoreService

    @State private var selectedId: String?
    @State private var showAddEntry = false
    @State private var showAddInstitution = false
    @State private var editingEntry: Entry?

    private var selected: Institution? {
        store.institutions.first { $0.id == selectedId } ?? store.institutions.first
    }
    private var filtered: [Entry] {
        guard let inst = selected else { return [] }
        return store.entries.filter { $0.institutionId == inst.id }
    }
    private var summary: Summary {
        SummaryCalc.summarize(
            filtered.map { .init(amount: $0.amount, type: $0.type, date: $0.date) },
            now: Date()
        )
    }

    var body: some View {
        NavigationStack {
            Group {
                if store.institutions.isEmpty {
                    ContentUnavailableView {
                        Label("Add your first institution", systemImage: "building.columns")
                    } description: {
                        Text("Create a ledger for a temple, church, library, or any organisation.")
                    } actions: {
                        Button("Create institution") { showAddInstitution = true }
                            .buttonStyle(.borderedProminent)
                    }
                } else {
                    List {
                        Section { hero }
                        Section {
                            if filtered.isEmpty {
                                Text("No entries yet.").foregroundStyle(.secondary)
                            } else {
                                ForEach(filtered) { e in
                                    Button { editingEntry = e } label: { entryRow(e) }
                                        .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Muthal")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if !store.institutions.isEmpty {
                        Picker("Institution",
                               selection: Binding(get: { selected?.id ?? "" },
                                                  set: { selectedId = $0 })) {
                            ForEach(store.institutions) { Text($0.name).tag($0.id) }
                        }
                    }
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { showAddInstitution = true } label: { Image(systemName: "plus.circle") }
                    Menu {
                        Button("Sign out", role: .destructive) { store.stop(); auth.signOut() }
                    } label: { Image(systemName: "ellipsis.circle") }
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if selected != nil {
                    Button { showAddEntry = true } label: {
                        Label("Add", systemImage: "plus").padding(.horizontal, 4)
                    }
                    .buttonStyle(.borderedProminent)
                    .padding()
                }
            }
            .sheet(isPresented: $showAddEntry) {
                if let inst = selected { EntrySheet(institution: inst, entry: nil) }
            }
            .sheet(item: $editingEntry) { e in
                let inst = store.institutions.first { $0.id == e.institutionId } ?? selected
                if let inst { EntrySheet(institution: inst, entry: e) }
            }
            .sheet(isPresented: $showAddInstitution) { InstitutionSheet() }
        }
    }

    private var hero: some View {
        let cur = selected?.currency ?? "INR"
        return VStack(alignment: .leading, spacing: 6) {
            Text("BALANCE").font(.caption.bold()).foregroundStyle(.secondary)
            Text(Currency.format(summary.balance, cur))
                .font(.system(size: 32, weight: .bold))
                .foregroundStyle(summary.balance < 0 ? Color.red : Color.primary)
            HStack {
                Text("This month  +\(Currency.format(summary.monthIncome, cur))")
                Spacer()
                Text("−\(Currency.format(summary.monthExpense, cur))")
            }
            .font(.footnote).foregroundStyle(.secondary)
        }
    }

    private func entryRow(_ e: Entry) -> some View {
        HStack {
            VStack(alignment: .leading) {
                Text(e.category.isEmpty ? "Uncategorized" : e.category).fontWeight(.semibold)
                if !e.note.isEmpty {
                    Text(e.note).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            Text((e.type == "income" ? "+" : "−") + Currency.format(e.amount, e.currency))
                .fontWeight(.bold)
                .foregroundStyle(e.type == "income" ? Color.accentColor : Color.red)
        }
    }
}
