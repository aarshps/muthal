import Foundation

/// Muthal's app-local preferences — the small surface the synced hora-core shared Swift
/// (`Haptics.swift` etc.) expects every family app to provide. Keys match the
/// `@AppStorage` keys used by `SettingsView` so both read the same UserDefaults values.
final class Preferences {
    static let shared = Preferences()
    private let defaults = UserDefaults.standard
    private init() {}

    private enum Key {
        static let hapticsEnabled = "haptics_enabled"
        static let biometricEnabled = "biometric_enabled"
        static let appearance = "appearance_mode"
    }

    var hapticsEnabled: Bool {
        get { defaults.object(forKey: Key.hapticsEnabled) as? Bool ?? true }
        set { defaults.set(newValue, forKey: Key.hapticsEnabled) }
    }

    var biometricEnabled: Bool {
        get { defaults.object(forKey: Key.biometricEnabled) as? Bool ?? false }
        set { defaults.set(newValue, forKey: Key.biometricEnabled) }
    }

    var appearanceMode: String {
        get { defaults.string(forKey: Key.appearance) ?? "system" }
        set { defaults.set(newValue, forKey: Key.appearance) }
    }
}
