import SwiftUI
import UIKit

/// Admin/owner-only member list (SPEC §3); only the owner can promote / demote / remove.
struct MembersView: View {
    @EnvironmentObject var auth: AuthService
    @EnvironmentObject var store: FirestoreService
    @Environment(\.dismiss) private var dismiss

    let institution: Institution

    @State private var members: [Member] = []
    @State private var target: Member?
    @State private var busy = false

    private var myUid: String? { auth.user?.uid }
    private var iAmOwner: Bool { institution.ownerId == myUid }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        VStack(alignment: .leading) {
                            Text("Join code").font(.caption).foregroundStyle(.secondary)
                            Text(institution.code).font(.title2.bold()).tracking(2)
                        }
                        Spacer()
                        Button { share() } label: { Image(systemName: "square.and.arrow.up") }
                    }
                }
                Section {
                    ForEach(members) { m in
                        Button {
                            guard iAmOwner, m.uid != myUid, m.role != .owner else { return }
                            target = m
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(m.displayName.isEmpty ? (m.email.isEmpty ? "Member" : m.email) : m.displayName)
                                        .foregroundStyle(.primary)
                                    if !m.email.isEmpty && !m.displayName.isEmpty {
                                        Text(m.email).font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                Text(m.role.label)
                                    .font(.caption.bold())
                                    .padding(.horizontal, 8).padding(.vertical, 3)
                                    .background(.secondary.opacity(0.15), in: Capsule())
                            }
                        }
                    }
                }
            }
            .navigationTitle("Manage members")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
            }
            .task { await load() }
            .confirmationDialog(target?.displayName ?? "Member", isPresented: Binding(get: { target != nil }, set: { if !$0 { target = nil } })) {
                if let target {
                    if target.role == .member {
                        Button("Promote to admin") { Task { await setRole(target, .admin) } }
                    }
                    if target.role == .admin {
                        Button("Demote to member") { Task { await setRole(target, .member) } }
                    }
                    Button("Remove member", role: .destructive) { Task { await remove(target) } }
                }
            }
        }
    }

    private func load() async {
        members = (try? await store.fetchMembers(instId: institution.id)) ?? []
    }

    private func setRole(_ m: Member, _ role: Role) async {
        busy = true
        try? await store.setMemberRole(instId: institution.id, memberUid: m.uid, role: role)
        await load()
        busy = false
    }

    private func remove(_ m: Member) async {
        busy = true
        try? await store.removeMember(instId: institution.id, memberUid: m.uid)
        await load()
        busy = false
    }

    private func share() {
        let text = "Join us on Muthal\n\nCode: \(institution.code)\n\(store.joinLink(institution.code))"
        let av = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?.keyWindow?.rootViewController?.present(av, animated: true)
    }
}
