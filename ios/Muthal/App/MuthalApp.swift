import SwiftUI
import FirebaseCore
import GoogleSignIn

@main
struct MuthalApp: App {
    @StateObject private var auth = AuthService()
    @StateObject private var store = FirestoreService()

    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(auth)
                .environmentObject(store)
                .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }
        }
    }
}
