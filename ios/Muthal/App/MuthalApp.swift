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
    // Google Sans (brand, rounded) vs the device's system font. Reads the same
    // UserDefaults key SettingsView's own @AppStorage writes, so toggling it there
    // invalidates this App's body too — matches Pathivu's PathivuApp.swift, which
    // gates .fontDesign the same way (Varisankya's iOS app never wires this at all,
    // so it isn't the reference here).
    @AppStorage("use_google_font") private var useGoogleFont = true

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
                .fontDesign(useGoogleFont ? .rounded : .default)
                .onOpenURL { url in
                    if GIDSignIn.sharedInstance.handle(url) { return }
                    // Join-link target (SPEC §2): muthal://join/{code} or
                    // https://muthal-web.vercel.app/join/{code}.
                    let segments = url.pathComponents.filter { $0 != "/" }
                    if url.host == "join", let code = segments.first {
                        store.pendingJoinCode = code.uppercased()
                    } else if let idx = segments.firstIndex(of: "join"), segments.count > idx + 1 {
                        store.pendingJoinCode = segments[idx + 1].uppercased()
                    }
                }
        }
    }
}
