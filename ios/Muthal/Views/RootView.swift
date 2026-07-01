import SwiftUI

struct RootView: View {
    @EnvironmentObject var auth: AuthService
    @EnvironmentObject var store: FirestoreService

    var body: some View {
        Group {
            if !auth.initialized {
                ProgressView()
            } else if auth.user == nil {
                SignInView()
            } else {
                HomeView()
                    .onAppear { if let uid = auth.user?.uid { store.start(uid: uid) } }
            }
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
