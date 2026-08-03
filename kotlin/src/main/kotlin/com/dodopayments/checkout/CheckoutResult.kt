package com.dodopayments.checkout

/**
 * The outcome of a checkout, derived entirely from the query string on the
 * merchant's `return_url`.
 *
 * This is a **UI signal only**. It is not proof of payment: the SDK never
 * calls the Dodo API and holds no API key. Grant access on your backend from
 * the webhook (`payment.succeeded` / `subscription.active`) or by retrieving
 * the payment with your secret key.
 */
enum class CheckoutStatus {
    /**
     * One-time payment settled (`status=succeeded`) or subscription became
     * active (`status=active`).
     */
    SUCCEEDED,

    /** The payment was declined (`status=failed`). */
    FAILED,

    /**
     * The user dismissed the checkout before any return URL arrived.
     *
     * **This is not a decline — do not show a failure screen for it.** The SDK
     * only ever learns the outcome from the return URL, so a dismissal leaves
     * the payment's real state unknown. The user may well have paid: closing
     * the tab while the hosted "Payment Successful" page counts down its
     * redirect produces exactly this status.
     *
     * Call [DodoCheckout.getAbandonedSession] for the `cks_…` session id,
     * reconcile it server-side, and show the outcome that comes back.
     */
    CANCELLED,

    /**
     * The payment will settle later — bank transfers and other async methods
     * (`status=processing` or any `requires_*`). The webhook delivers the
     * final outcome.
     */
    PENDING,

    /** The checkout session expired before completion. */
    EXPIRED
}

/**
 * The result handed back from [DodoCheckout.start].
 *
 * Everything here comes from the `return_url` query parameters. [raw] holds
 * the complete, unmodified parameter set so callers can read fields the typed
 * surface does not model.
 */
data class CheckoutResult(
    val status: CheckoutStatus,
    val paymentId: String? = null,
    val subscriptionId: String? = null,
    val licenseKeys: List<String>? = null,
    val customerEmail: String? = null,
    /** All query parameters from the `return_url`, verbatim. */
    val raw: Map<String, String> = emptyMap()
)
