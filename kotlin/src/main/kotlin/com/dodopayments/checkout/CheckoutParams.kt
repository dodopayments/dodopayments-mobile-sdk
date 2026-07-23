package com.dodopayments.checkout

/** The input to [DodoCheckout.start] / [DodoCheckout.contract]. */
data class CheckoutParams(
    /**
     * The session URL from your backend. Must be a
     * `checkout.dodopayments.com` / `test.checkout.dodopayments.com`
     * `/session/…` URL.
     */
    val checkoutUrl: String,
    /**
     * The URL the SDK watches for. Any absolute URL the checkout ends up
     * navigating to (the session's `return_url`); matched on
     * scheme+host+path. It never has to resolve — a sentinel is fine. Its
     * scheme must be redeclared on [BrowserRedirectActivity]'s manifest
     * intent-filter in your own app, so the OS routes the redirect back to
     * this app — see that class's kdoc for the exact block to add.
     */
    val returnUrl: String
)
