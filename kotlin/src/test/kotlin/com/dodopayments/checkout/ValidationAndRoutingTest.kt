package com.dodopayments.checkout

import org.junit.Assert.assertEquals
import org.junit.Test

/** U10–U12: input validation and single-checkout guard. */
class ValidationAndRoutingTest {

    private fun expectError(code: CheckoutError.Code, block: () -> Unit) {
        try {
            block()
            org.junit.Assert.fail("expected CheckoutError $code")
        } catch (e: CheckoutError) {
            assertEquals(code, e.code)
        }
    }

    // U10: non-dodopayments checkoutUrl → INVALID_CHECKOUT_URL.
    @Test
    fun invalidCheckoutUrlThrows() {
        expectError(CheckoutError.Code.INVALID_CHECKOUT_URL) {
            UrlValidator.validateCheckoutUrl("https://evil.com/session/cks_1")
        }
    }

    @Test
    fun checkoutUrlWrongPathThrows() {
        expectError(CheckoutError.Code.INVALID_CHECKOUT_URL) {
            UrlValidator.validateCheckoutUrl("https://checkout.dodopayments.com/not-a-session")
        }
    }

    @Test
    fun checkoutUrlHttpSchemeThrows() {
        expectError(CheckoutError.Code.INVALID_CHECKOUT_URL) {
            UrlValidator.validateCheckoutUrl("http://checkout.dodopayments.com/session/cks_1")
        }
    }

    @Test
    fun validCheckoutUrlsPass() {
        UrlValidator.validateCheckoutUrl("https://checkout.dodopayments.com/session/cks_live_1")
        UrlValidator.validateCheckoutUrl("https://test.checkout.dodopayments.com/session/cks_test_1")
    }

    // U11: malformed returnUrl → INVALID_RETURN_URL.
    @Test
    fun invalidReturnUrlThrows() {
        expectError(CheckoutError.Code.INVALID_RETURN_URL) {
            UrlValidator.validateReturnUrl("not-a-url")
        }
    }

    @Test
    fun validReturnUrlPasses() {
        UrlValidator.validateReturnUrl("https://myapp.com/checkout/return")
        // A custom-scheme sentinel is still a valid absolute URL.
        UrlValidator.validateReturnUrl("myapp://checkout/return")
    }

    // U12: second concurrent checkout → ALREADY_IN_PROGRESS.
    @Test
    fun secondStartThrowsAlreadyInProgress() {
        val guard = InProgressGuard()
        guard.begin()
        expectError(CheckoutError.Code.ALREADY_IN_PROGRESS) { guard.begin() }
        guard.end()
        // After ending, a new checkout is allowed again.
        guard.begin()
        guard.end()
    }

    @Test
    fun returnUrlMatcherMatchesReturnUrl() {
        val matcher = ReturnUrlMatcher("https://myapp.com/checkout/return")
        val nav = "https://myapp.com/checkout/return?payment_id=pay_1&status=succeeded"
        assertEquals(true, matcher.matches(nav))
        val result = ResultParser.parse(nav)
        assertEquals(CheckoutStatus.SUCCEEDED, result.status)
        assertEquals("pay_1", result.paymentId)
    }
}
