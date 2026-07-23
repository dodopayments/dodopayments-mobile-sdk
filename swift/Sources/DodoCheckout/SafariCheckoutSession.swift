#if canImport(UIKit)
import SafariServices
import UIKit

/// Backs `DodoCheckout.start`: an in-app `SFSafariViewController` sheet.
/// Chosen over `ASWebAuthenticationSession` — no misleading "wants to Sign
/// In" system dialog, confirmed Apple Pay support, and controlled testing
/// (iOS 26.5 Simulator: a 90s delayed redirect, with and without intervening
/// page activity) did not reproduce the redirect-drop behavior some older
/// reports describe. Not yet verified on a physical device.
@MainActor
final class SafariCheckoutSession: NSObject {
    private let matcher: ReturnUrlMatcher
    private let onEvent: (@Sendable (CheckoutEvent) -> Void)?

    private weak var safariViewController: SFSafariViewController?
    private var continuation: CheckedContinuation<CheckoutResult, Never>?
    private var resumed = false

    init(returnUrl: URL, onEvent: (@Sendable (CheckoutEvent) -> Void)?) {
        self.matcher = ReturnUrlMatcher(returnUrl: returnUrl)
        self.onEvent = onEvent
    }

    func start(checkoutUrl: URL, presenter: UIViewController) async -> CheckoutResult {
        onEvent?(.opened)
        return await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                self.continuation = continuation
                let safari = SFSafariViewController(url: checkoutUrl)
                safari.delegate = self
                safari.modalPresentationStyle = .pageSheet
                safariViewController = safari
                presenter.present(safari, animated: true)
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                self?.finish(with: CheckoutResult(status: .cancelled), dismiss: true)
            }
        }
    }

    /// Called by `DodoCheckout.handleOpenURL`. Returns `true` if this session
    /// claimed the URL.
    func handleOpenURL(_ url: URL) -> Bool {
        guard matcher.matches(url) else { return false }
        onEvent?(.returnReceived)
        finish(with: ResultParser.parse(url: url), dismiss: true)
        return true
    }

    private func finish(with result: CheckoutResult, dismiss: Bool) {
        guard !resumed else { return }
        resumed = true
        onEvent?(.closed)
        let continuation = self.continuation
        self.continuation = nil
        guard dismiss, let safari = safariViewController, safari.presentingViewController != nil else {
            continuation?.resume(returning: result)
            return
        }
        safari.dismiss(animated: true) {
            continuation?.resume(returning: result)
        }
    }
}

extension SafariCheckoutSession: SFSafariViewControllerDelegate {
    // `nonisolated` + `assumeIsolated`, not plain `@MainActor`: this class's
    // conformance to a non-isolated protocol only compiles without an
    // explicit hop when the `InferIsolatedConformances` upcoming feature is
    // enabled — true in swift/Package.swift, but not guaranteed in every
    // consumer's build config (RN/Flutter's podspecs don't set it). UIKit
    // always calls delegate methods on the main thread, so the assumption is
    // safe portably, independent of that flag.
    nonisolated func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
        MainActor.assumeIsolated {
            // The system is already dismissing the view controller here —
            // don't dismiss again, just resume.
            finish(with: CheckoutResult(status: .cancelled), dismiss: false)
        }
    }
}
#endif
