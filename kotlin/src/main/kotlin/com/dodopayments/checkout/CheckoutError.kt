package com.dodopayments.checkout

/**
 * Errors thrown by [DodoCheckout.start].
 *
 * A user cancelling or a payment failing is a **result** ([CheckoutStatus]),
 * never an error. These cases only cover misuse and platform failures.
 */
class CheckoutError(
    val code: Code,
    message: String = code.name
) : Exception(message) {

    enum class Code {
        /**
         * `checkoutUrl` is not a `checkout.dodopayments.com` /
         * `test.checkout.dodopayments.com` session URL.
         */
        INVALID_CHECKOUT_URL,

        /** `returnUrl` is not a valid absolute URL. */
        INVALID_RETURN_URL,

        /** A checkout is already running. Only one can run at a time. */
        ALREADY_IN_PROGRESS,

        /** An unexpected platform error occurred (e.g. the activity could not launch). */
        PLATFORM_ERROR
    }

    override fun toString(): String = "${code.name}: $message"
}
