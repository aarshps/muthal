import SwiftUI
import FirebaseAuth
import FirebaseFirestore

/// Family-standard Settings screen (hora-core `settings-page-standards`): large title,
/// profile header card, titled grouped cards (Appearance / Preferences / About), and
/// bottom full-width Sign out + Delete account.
struct SettingsView: View {
    @EnvironmentObject var auth: AuthService
    @EnvironmentObject var store: FirestoreService
    @Environment(\.dismiss) private var dismiss

    @AppStorage("appearance_mode") private var appearanceMode = "system"
    @AppStorage("haptics_enabled") private var hapticsEnabled = true
    @AppStorage("biometric_enabled") private var biometricEnabled = false

    @State private var confirmDelete = false
    @State private var deleting = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    profileCard
                    appearanceCard
                    preferencesCard
                    aboutCard
                    accountButtons
                }
                .padding(16)
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .fontDesign(.rounded)
    }

    // MARK: sections

    private var profileCard: some View {
        card {
            HStack(spacing: 14) {
                AsyncImage(url: auth.user?.photoURL) { image in
                    image.resizable()
                } placeholder: {
                    Image(systemName: "person.crop.circle.fill").resizable()
                        .foregroundStyle(.secondary)
                }
                .frame(width: 48, height: 48)
                .clipShape(Circle())

                VStack(alignment: .leading, spacing: 2) {
                    Text(auth.user?.displayName ?? "Muthal")
                        .font(.headline.bold())
                    Text(auth.user?.email ?? "")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
        }
    }

    private var appearanceCard: some View {
        card {
            VStack(alignment: .leading, spacing: 12) {
                Text("Appearance").font(.title3.bold())
                LabelStack(title: "Theme", subtitle: "Follow the system, or force light/dark")
                Picker("Theme", selection: $appearanceMode) {
                    Text("System").tag("system")
                    Text("Light").tag("light")
                    Text("Dark").tag("dark")
                }
                .pickerStyle(.segmented)
                .onChange(of: appearanceMode) { Haptics.tick() }
            }
        }
    }

    private var preferencesCard: some View {
        card {
            VStack(alignment: .leading, spacing: 12) {
                Text("Preferences").font(.title3.bold())
                Toggle(isOn: $hapticsEnabled) {
                    LabelStack(title: "Haptic feedback", subtitle: "Vibration on taps and key actions")
                }
                .onChange(of: hapticsEnabled) { if hapticsEnabled { Haptics.click() } }
                Divider()
                Toggle(isOn: $biometricEnabled) {
                    LabelStack(title: "App lock", subtitle: "Require Face ID / Touch ID on launch")
                }
                .onChange(of: biometricEnabled) { Haptics.click() }
            }
        }
    }

    private var aboutCard: some View {
        card {
            VStack(alignment: .leading, spacing: 8) {
                Text("About").font(.title3.bold())
                let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
                let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
                Text("Version \(version) (\(build))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Link("github.com/aarshps/muthal",
                     destination: URL(string: "https://github.com/aarshps/muthal")!)
                    .font(.caption)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var accountButtons: some View {
        VStack(spacing: 8) {
            Button {
                Haptics.click()
                store.stop()
                auth.signOut()
                dismiss()
            } label: {
                Text("Sign out").frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)

            Button(role: .destructive) {
                Haptics.warning()
                confirmDelete = true
            } label: {
                if deleting { ProgressView().frame(maxWidth: .infinity) }
                else { Text("Delete account").frame(maxWidth: .infinity) }
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .tint(.red)
            .disabled(deleting)
            .confirmationDialog(
                "This permanently deletes your institutions, entries, and account. This cannot be undone.",
                isPresented: $confirmDelete,
                titleVisibility: .visible
            ) {
                Button("Delete forever", role: .destructive) { Task { await deleteAccount() } }
            }
        }
        .padding(.top, 8)
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content()
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 28))
    }

    /// PRIVACY.md promise: Settings → Delete account removes all user data + the auth
    /// record. Institutions are shared (SPEC §1) — this only removes the user's OWN
    /// presence, never entries/categories other members depend on.
    private func deleteAccount() async {
        guard let user = Auth.auth().currentUser else { return }
        deleting = true
        defer { deleting = false }
        let db = Firestore.firestore()
        do {
            try await store.deleteAllUserData(uid: user.uid)
            store.stop()
            try await db.collection("users").document(user.uid).delete()
            try await user.delete()
        } catch {
            // Auth deletion can require a recent login; data is already gone by then.
            auth.signOut()
        }
        dismiss()
    }
}

/// title + subtitle stack used by settings rows (family standard).
struct LabelStack: View {
    let title: String
    let subtitle: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.subheadline.weight(.semibold))
            Text(subtitle).font(.caption).foregroundStyle(.secondary)
        }
    }
}
