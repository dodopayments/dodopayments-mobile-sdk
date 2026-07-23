package com.dodopayments.checkout

/**
 * Lifecycle events emitted during a checkout, for logging/analytics only.
 *
 * **Never decide the outcome from an event.** Use the [CheckoutResult]
 * returned by [DodoCheckout.start]. Events carry host-only URL info, never
 * full URLs with query strings.
 */
sealed class CheckoutEvent(
    /** Stable string name, matching the cross-platform event vocabulary. */
    val name: String
) {
    /** The checkout screen was presented. */
    data object Opened : CheckoutEvent("checkout.opened")

    /**
     * Debug trace: a top-level (main-frame) page transition occurred, carrying
     * the host only. Fires several times during a checkout as it hops through
     * third-party pages (3DS, redirect PSPs). Purely for debugging the flow —
     * do not drive any logic from it.
     */
    data class Navigation(val host: String) : CheckoutEvent("checkout.navigation")

    /** A navigation matching `returnUrl` was intercepted. */
    data object ReturnReceived : CheckoutEvent("checkout.return_received")

    /** The checkout screen was dismissed. */
    data object Closed : CheckoutEvent("checkout.closed")
}
