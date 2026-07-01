import Foundation
import UIKit
import FirebaseAuth
import FirebaseCore
import GoogleSignIn

/// Google sign-in via GoogleSignIn + Firebase Auth. Mirrors the web/Android auth layer.
@MainActor
final class AuthService: ObservableObject {
    @Published var user: User?
    @Published var initialized = false

    private var handle: AuthStateDidChangeListenerHandle?

    init() {
        handle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            self?.user = user
            self?.initialized = true
        }
    }

    func signIn() {
        guard let clientID = FirebaseApp.app()?.options.clientID else { return }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first?.keyWindow?.rootViewController else { return }

        GIDSignIn.sharedInstance.signIn(withPresenting: root) { result, error in
            guard error == nil,
                  let result,
                  let idToken = result.user.idToken?.tokenString else { return }
            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: result.user.accessToken.tokenString
            )
            Auth.auth().signIn(with: credential)
        }
    }

    func signOut() {
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
    }
}
