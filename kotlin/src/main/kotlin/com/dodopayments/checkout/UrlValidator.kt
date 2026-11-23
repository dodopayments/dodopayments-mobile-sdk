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

    /** `confirm: true` sessions return `/{paymentLinkId}` instead of `/session/…`. */
    private val PAYMENT_LINK_PATH = Regex("^/[A-Za-z0-9_-]{8}/?$")

    /**
     * Throws [CheckoutError] with [CheckoutError.Code.INVALID_CHECKOUT_URL]
     * unless [url] is an https Dodo checkout URL on an allowed host whose path
     * is `/session/…` or an 8-character payment link id.
     */
    fun validateCheckoutUrl(url: String) {
        val parts = UrlParts.from(url)
        val scheme = parts.scheme?.lowercase()
        val host = parts.host?.lowercase()
        val path = parts.path.orEmpty()
        val valid = scheme == "https" &&
            host != null && host in ALLOWED_CHECKOUT_HOSTS &&
            (path.startsWith("/session/") || PAYMENT_LINK_PATH.matches(path))
        if (!valid) {
            throw CheckoutError(
                CheckoutError.Code.INVALID_CHECKOUT_URL,
                "checkoutUrl must be a checkout.dodopayments.com or test.checkout.dodopayments.com /session/ or payment link URL."
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
