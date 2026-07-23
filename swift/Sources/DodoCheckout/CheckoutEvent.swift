import Foundation

/// Lifecycle events emitted during a checkout, for logging/analytics only.
///
/// **Never decide the outcome from an event.** Use the `CheckoutResult`
/// returned by `start`. Events carry host-only URL info, never full URLs with
/// query strings.
public enum CheckoutEvent: Sendable, Equatable {
    /// The checkout screen was presented.
    case opened
    /// Debug trace: a top-level (main-frame) page transition occurred, carrying
    /// the host only. Fires several times during a checkout as it hops through
    /// third-party pages (3DS, redirect PSPs). Purely for debugging the flow —
    /// do not drive any logic from it.
    case navigation(host: String)
    /// A navigation matching `returnUrl` was intercepted.
    case returnReceived
    /// The checkout screen was dismissed.
    case closed

    /// Stable string name, matching the cross-platform event vocabulary.
    public var name: String {
        switch self {
        case .opened: return "checkout.opened"
        case .navigation: return "checkout.navigation"
        case .returnReceived: return "checkout.return_received"
        case .closed: return "checkout.closed"
        }
    }
}
