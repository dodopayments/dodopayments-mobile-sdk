package com.dodopayments.checkout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** U1–U4: return-url matching. */
class ReturnUrlMatcherTest {

    private val returnUrl = "https://myapp.com/checkout/return"

    // U1: matches the return_url even with a query string appended.
    @Test
    fun matchesReturnUrlWithQuery() {
        val matcher = ReturnUrlMatcher(returnUrl)
        assertTrue(
            matcher.matches(
                "https://myapp.com/checkout/return?payment_id=pay_123&status=succeeded&email=a@b.com"
            )
        )
    }

    // U2: does NOT match the intermediate /return/{id} hop on the Dodo backend host.
    @Test
    fun doesNotMatchDodoReturnHop() {
        val matcher = ReturnUrlMatcher(returnUrl)
        assertFalse(matcher.matches("https://test.dodopayments.com/return/pay_123?status=succeeded"))
    }

    // U3: does NOT match an unrelated checkout/redirect URL.
    @Test
    fun doesNotMatchUnrelatedUrl() {
        val matcher = ReturnUrlMatcher(returnUrl)
        assertFalse(matcher.matches("https://sandbox.cashfree.com/pg/view/payment/abc"))
    }

    // U4: host case and a trailing slash are normalized; still matches.
    @Test
    fun normalizesHostCaseAndTrailingSlash() {
        val matcher = ReturnUrlMatcher(returnUrl)
        assertTrue(matcher.matches("https://MyApp.COM/checkout/return/?status=succeeded"))
    }

    @Test
    fun differentPathDoesNotMatch() {
        val matcher = ReturnUrlMatcher(returnUrl)
        assertFalse(matcher.matches("https://myapp.com/checkout/other"))
    }

    @Test
    fun sentinelReturnUrlMatches() {
        val matcher = ReturnUrlMatcher("https://dodo-sdk-webview-return.example.com/done")
        assertTrue(
            matcher.matches(
                "https://dodo-sdk-webview-return.example.com/done?payment_id=pay_1&status=succeeded"
            )
        )
    }

    @Test
    fun schemeIsCompared() {
        val matcher = ReturnUrlMatcher(returnUrl)
        assertFalse(matcher.matches("http://myapp.com/checkout/return?status=succeeded"))
    }

    @Test
    fun emptyPathNormalizesToSlash() {
        val matcher = ReturnUrlMatcher("https://myapp.com")
        assertTrue(matcher.matches("https://myapp.com/?status=succeeded"))
    }
}
