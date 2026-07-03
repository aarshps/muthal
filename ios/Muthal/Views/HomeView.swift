import SwiftUI
import UIKit

struct HomeView: View {
    @EnvironmentObject var auth: AuthService
    @EnvironmentObject var store: FirestoreService

    @State private var showAddEntry = false
    @State private var showAddInstitution = false
    @State private var showJoin = false
    @State private var showSettings = false
    @State private var showMembers = false
    @State private var showCategories = false
    @State private var showExport = false
    @State private var editingEntry: Entry?
    @State private var confirmDeleteInstitution = false

    private var selected: Membership? {
        store.memberships.first { $0.institutionId == store.institution?.id } ?? store.memberships.first
    }
    private var canEdit: Bool { selected?.role.isAdminOrOwner ?? false }
    private var summary: Summary {
        SummaryCalc.summarize(
            store.entries.map { .init(amount: $0.amount, type: $0.type, date: $0.date) },
            now: Date()
        )
    }

    var body: some View {
        NavigationStack {
            Group {
                if store.memberships.isEmpty {
                    ContentUnavailableView {
                        Label("Add your first institution", systemImage: "building.columns")
                    } description: {
                        Text("Create a ledger for a temple, church, library, or any organisation — or join one with a code.")
                    } actions: {
                        Button("Create institution") { showAddInstitution = true }
                            .buttonStyle(.borderedProminent)
                        Button("Join with a code") { showJoin = true }
                    }
                } else if let inst = store.institution {
                    List {
                        Section { hero(inst) }
                        Section {
                            if store.entries.isEmpty {
                                Text(canEdit ? "No entries yet." : "Entries added by an admin will appear here.")
                                    .foregroundStyle(.secondary)
                            } else {
                                ForEach(store.entries) { e in
                                    if canEdit {
                                        Button { editingEntry = e } label: { entryRow(e, inst) }
                                            .buttonStyle(.plain)
                                    } else {
                                        entryRow(e, inst)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Muthal")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if !store.memberships.isEmpty {
                        Menu {
                            ForEach(store.memberships) { m in
                                Button {
                                    store.selectInstitution(m.institutionId)
                                } label: {
                                    if m.institutionId == selected?.institutionId {
                                        Label(m.institutionName, systemImage: "checkmark")
                                    } else {
                                        Text(m.institutionName)
                                    }
                                }
                            }
                            Divider()
                            Button("Create institution") { showAddInstitution = true }
                            Button("Join with a code") { showJoin = true }
                        } label: {
                            HStack(spacing: 4) {
                                Text(selected?.institutionName ?? "Muthal").fontWeight(.semibold)
                                Image(systemName: "chevron.down")
                            }
                        }
                    }
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if let selected {
                        Menu {
                            Button { shareInstitution() } label: { Label("Share institution", systemImage: "square.and.arrow.up") }
                            if selected.role.isAdminOrOwner {
                                Button { showMembers = true } label: { Label("Manage members", systemImage: "person.2") }
                                Button { showCategories = true } label: { Label("Categories", systemImage: "list.bullet") }
                            }
                            Button { showExport = true } label: { Label("Period export", systemImage: "calendar") }
                            Button(role: .destructive) { leaveInstitution() } label: { Label("Leave institution", systemImage: "rectangle.portrait.and.arrow.right") }
                            if selected.role == .owner {
                                Button(role: .destructive) { confirmDeleteInstitution = true } label: { Label("Delete institution", systemImage: "trash") }
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                        }
                    }
                    // Family standard: profile/settings entry on the trailing edge;
                    // sign-out lives inside Settings, never on the home toolbar.
                    Button { showSettings = true } label: {
                        AsyncImage(url: auth.user?.photoURL) { image in
                            image.resizable()
                        } placeholder: {
                            Image(systemName: "person.crop.circle")
                        }
                        .frame(width: 28, height: 28)
                        .clipShape(Circle())
                    }
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if canEdit {
                    Button { showAddEntry = true } label: {
                        Label("Add", systemImage: "plus").padding(.horizontal, 4)
                    }
                    .buttonStyle(.borderedProminent)
                    .padding()
                }
            }
            .sheet(isPresented: $showAddEntry) {
                if let inst = store.institution { EntrySheet(institution: inst, entry: nil) }
            }
            .sheet(item: $editingEntry) { e in
                if let inst = store.institution { EntrySheet(institution: inst, entry: e) }
            }
            .sheet(isPresented: $showAddInstitution) { InstitutionSheet() }
            .sheet(isPresented: $showJoin) { JoinSheet(prefillCode: store.pendingJoinCode) }
            .sheet(isPresented: $showSettings) { SettingsView() }
            .sheet(isPresented: $showMembers) {
                if let inst = store.institution { MembersView(institution: inst) }
            }
            .sheet(isPresented: $showCategories) {
                if let inst = store.institution { CategoriesView(institution: inst) }
            }
            .sheet(isPresented: $showExport) {
                if let inst = store.institution { PeriodExportView(institution: inst) }
            }
            .onChange(of: store.memberships) { _, list in
                guard let inst = store.institution else {
                    if let first = list.first { store.selectInstitution(first.institutionId) }
                    return
                }
                if !list.contains(where: { $0.institutionId == inst.id }), let first = list.first {
                    store.selectInstitution(first.institutionId)
                }
            }
            .onChange(of: store.pendingJoinCode) { _, code in
                if code != nil { showJoin = true }
            }
            .confirmationDialog(
                "Permanently delete \"\(selected?.institutionName ?? "")\"? This removes every entry, category, and member. This cannot be undone.",
                isPresented: $confirmDeleteInstitution,
                titleVisibility: .visible
            ) {
                Button("Delete forever", role: .destructive) { Task { await deleteInstitution() } }
            }
        }
    }

    private func hero(_ inst: Institution) -> some View {
        let cur = inst.currency
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

    private func entryRow(_ e: Entry, _ inst: Institution) -> some View {
        HStack {
            VStack(alignment: .leading) {
                Text(e.category.isEmpty ? "Uncategorized" : e.category).fontWeight(.semibold)
                if !e.note.isEmpty {
                    Text(e.note).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            Text((e.type == "income" ? "+" : "−") + Currency.format(e.amount, inst.currency))
                .fontWeight(.bold)
                .foregroundStyle(e.type == "income" ? Color.accentColor : Color.red)
        }
    }

    private func shareInstitution() {
        guard let inst = store.institution else { return }
        let text = "Join \"\(inst.name)\" on Muthal\n\nCode: \(inst.code)\n\(store.joinLink(inst.code))"
        let av = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?.keyWindow?.rootViewController?.present(av, animated: true)
    }

    private func leaveInstitution() {
        guard let selected, let uid = auth.user?.uid else { return }
        Task { try? await store.removeMember(instId: selected.institutionId, memberUid: uid) }
    }

    private func deleteInstitution() async {
        guard let selected else { return }
        try? await store.deleteInstitution(instId: selected.institutionId)
    }
}
