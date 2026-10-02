import SwiftUI
import ComposeApp
import GoogleSignIn

@main
struct HabitSheetApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRootView()
                .ignoresSafeArea(.container, edges: .all)
                .ignoresSafeArea(.keyboard)
                .onOpenURL { url in
                    _ = GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}

private struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let tokenProvider = IosSheetTokenProvider()
        let controller = MainViewControllerKt.MainViewController(tokenProvider: tokenProvider)
        tokenProvider.presentingController = controller
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private final class IosSheetTokenProvider: NSObject, SheetTokenProvider {
    private let sheetsScope = "https://www.googleapis.com/auth/spreadsheets"
    weak var presentingController: UIViewController?

    func requestToken(interactive: Bool, completion: @escaping (String?, String?) -> Void) {
        Task { @MainActor in
            do {
                var user = GIDSignIn.sharedInstance.currentUser
                if user == nil {
                    if interactive {
                        guard let controller = presentingController else {
                            completion(nil, "Sign-in screen is unavailable.")
                            return
                        }
                        user = try await GIDSignIn.sharedInstance.signIn(
                            withPresenting: controller,
                            hint: nil,
                            additionalScopes: [sheetsScope]
                        ).user
                    } else {
                        user = try? await GIDSignIn.sharedInstance.restorePreviousSignIn()
                    }
                }
                guard var user else {
                    completion(nil, nil)
                    return
                }
                if !(user.grantedScopes ?? []).contains(sheetsScope) {
                    guard interactive, let controller = presentingController else {
                        completion(nil, nil)
                        return
                    }
                    user = try await user.addScopes([sheetsScope], presenting: controller).user
                }
                user = try await user.refreshTokensIfNeeded()
                completion(user.accessToken.tokenString, nil)
            } catch {
                completion(nil, "Google sign-in failed or was cancelled.")
            }
        }
    }
}
