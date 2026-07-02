import SwiftUI

struct RootView: View {
    @EnvironmentObject var auth: AuthService
    @EnvironmentObject var store: FirestoreService

    // App-Lock gate (family standard): biometric before any content when enabled.
    @AppStorage("biometric_enabled") private var biometricEnabled = false
    @State private var unlocked = false
    @State private var unlocking = false

    var body: some View {
        Group {
            if !auth.initialized {
                // Boot frame while auth resolves (splash-and-home-standards).
                bootFrame
            } else if auth.user == nil {
                SignInView()
            } else if biometricEnabled && !unlocked {
                bootFrame
                    .task {
                        guard !unlocking else { return }
                        unlocking = true
                        unlocked = await BiometricAuth.authenticate()
                        unlocking = false
                    }
            } else {
                HomeView()
                    .onAppear { if let uid = auth.user?.uid { store.start(uid: uid) } }
            }
        }
    }

    private var bootFrame: some View {
        VStack(spacing: 12) {
            Image(systemName: "indianrupeesign.circle.fill")
                .resizable().frame(width: 72, height: 72)
                .foregroundStyle(.tint)
            Text("Muthal").font(.largeTitle.bold())
        }
    }
}

struct SignInView: View {
    @EnvironmentObject var auth: AuthService

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "indianrupeesign.circle.fill")
                .resizable().frame(width: 72, height: 72)
                .foregroundStyle(.tint)
            Text("Muthal").font(.largeTitle.bold())
            Text("Income & expense tracking for institutions.")
                .font(.subheadline).foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button {
                auth.signIn()
            } label: {
                Label("Continue with Google", systemImage: "person.crop.circle")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .padding(.top, 8)
        }
        .padding(32)
    }
}
