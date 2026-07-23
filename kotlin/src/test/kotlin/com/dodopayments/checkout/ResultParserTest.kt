package com.dodopayments.checkout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** U5–U9: parsing the return_url query into a result. */
class ResultParserTest {

    private fun parse(query: String): CheckoutResult =
        ResultParser.parse("https://myapp.com/checkout/return?$query")

    // U5: one-time payment.
    @Test
    fun succeededOneTime() {
        val result = parse("payment_id=pay_123&status=succeeded")
        assertEquals(CheckoutStatus.SUCCEEDED, result.status)
        assertEquals("pay_123", result.paymentId)
        assertNull(result.subscriptionId)
    }

    // U6: subscription (status=active).
    @Test
    fun succeededSubscription() {
        val result = parse("subscription_id=sub_456&status=active")
        assertEquals(CheckoutStatus.SUCCEEDED, result.status)
        assertEquals("sub_456", result.subscriptionId)
        assertNull(result.paymentId)
    }

    // U7: failed.
    @Test
    fun failed() {
        val result = parse("payment_id=pay_9&status=failed")
        assertEquals(CheckoutStatus.FAILED, result.status)
    }

    // U8: processing / requires_* → pending.
    @Test
    fun processingIsPending() {
        assertEquals(CheckoutStatus.PENDING, parse("status=processing").status)
    }

    @Test
    fun requiresActionIsPending() {
        assertEquals(CheckoutStatus.PENDING, parse("status=requires_action").status)
    }

    // U9: license_key + email populated in result and raw.
    @Test
    fun licenseKeyAndEmailPopulated() {
        val result = parse("payment_id=pay_1&status=succeeded&license_key=LIC-XYZ&email=user%40example.com")
        assertEquals(listOf("LIC-XYZ"), result.licenseKeys)
        assertEquals("user@example.com", result.customerEmail)
        assertEquals("LIC-XYZ", result.raw["license_key"])
        assertEquals("user@example.com", result.raw["email"])
        assertEquals("pay_1", result.raw["payment_id"])
    }

    @Test
    fun multipleLicenseKeysCollected() {
        val result = parse("status=succeeded&license_key=LIC-1&license_key=LIC-2")
        assertEquals(listOf("LIC-1", "LIC-2"), result.licenseKeys)
    }

    @Test
    fun expiredStatus() {
        assertEquals(CheckoutStatus.EXPIRED, parse("status=expired").status)
    }

    // Unknown/missing status defaults to pending (webhook is authoritative).
    @Test
    fun unknownStatusDefaultsToPending() {
        assertEquals(CheckoutStatus.PENDING, parse("payment_id=pay_1").status)
        assertEquals(CheckoutStatus.PENDING, parse("status=some_new_state").status)
    }

    // raw carries every param verbatim.
    @Test
    fun rawCarriesAllParams() {
        val result = parse("payment_id=pay_1&status=succeeded&foo=bar")
        assertEquals("bar", result.raw["foo"])
    }

    @Test
    fun noQueryIsPendingWithEmptyRaw() {
        val result = ResultParser.parse("https://myapp.com/checkout/return")
        assertEquals(CheckoutStatus.PENDING, result.status)
        assertEquals(emptyMap<String, String>(), result.raw)
    }
}
