import SwiftUI
import FirebaseCore
import GoogleSignIn

@main
struct MuthalApp: App {
    @StateObject private var auth = AuthService()
    @StateObject private var store = FirestoreService()

    // Family standard (settings-page-standards): the Appearance choice persists and
    // is applied at the scene root.
    @AppStorage("appearance_mode") private var appearanceMode = "system"

    init() {
        FirebaseApp.configure()
    }

    private var colorScheme: ColorScheme? {
        switch appearanceMode {
        case "light": .light
        case "dark": .dark
        default: nil
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(auth)
                .environmentObject(store)
                .preferredColorScheme(colorScheme)
                .fontDesign(.rounded)
                .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }
        }
    }
}
