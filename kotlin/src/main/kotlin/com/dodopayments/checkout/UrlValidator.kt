package com.dodopayments.checkout

/** Validates the input passed to `start` before any UI is launched. */
internal object UrlValidator {

    /**
     * Hosts that a valid `checkoutUrl` may use. Test vs live is read from the
     * host — there is no mode flag.
     */
    val ALLOWED_CHECKOUT_HOSTS: Set<String> = setOf(
        "checkout.dodopayments.com",
        "test.checkout.dodopayments.com"
    )

    /**
     * Throws [CheckoutError] with [CheckoutError.Code.INVALID_CHECKOUT_URL]
     * unless [url] is an https Dodo checkout session URL (`/session/…` path on
     * an allowed host).
     */
    fun validateCheckoutUrl(url: String) {
        val parts = UrlParts.from(url)
        val scheme = parts.scheme?.lowercase()
        val host = parts.host?.lowercase()
        val valid = scheme == "https" &&
            host != null && host in ALLOWED_CHECKOUT_HOSTS &&
            parts.path?.startsWith("/session/") == true
        if (!valid) {
            throw CheckoutError(
                CheckoutError.Code.INVALID_CHECKOUT_URL,
                "checkoutUrl must be a checkout.dodopayments.com or test.checkout.dodopayments.com /session/ URL."
            )
        }
    }

    /**
     * Throws [CheckoutError] with [CheckoutError.Code.INVALID_RETURN_URL]
     * unless [url] is a well-formed absolute URL with both a scheme and a
     * host. The URL need not resolve — a sentinel is fine, since the SDK
     * cancels the navigation before it loads.
     */
    fun validateReturnUrl(url: String) {
        val parts = UrlParts.from(url)
        if (parts.scheme.isNullOrEmpty() || parts.host.isNullOrEmpty()) {
            throw CheckoutError(
                CheckoutError.Code.INVALID_RETURN_URL,
                "returnUrl must be a valid absolute URL with a scheme and host."
            )
        }
    }
}
