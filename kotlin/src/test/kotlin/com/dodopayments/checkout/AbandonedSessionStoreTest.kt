package com.dodopayments.checkout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) {
        values[key] = value
    }
    override fun remove(key: String) {
        values.remove(key)
    }
}

/** Logic behind E10 (recover an abandoned session) without process death. */
class AbandonedSessionStoreTest {

    @Test
    fun extractsSessionIdFromCheckoutUrl() {
        assertEquals(
            "cks_abc123",
            AbandonedSessionStore.sessionIdFrom("https://test.checkout.dodopayments.com/session/cks_abc123")
        )
    }

    @Test
    fun recordThenCurrentReturnsSession() {
        val subject = AbandonedSessionStore(FakeKeyValueStore())
        val created = 1_700_000_000_000L
        subject.record("https://checkout.dodopayments.com/session/cks_xyz", atMillis = created)

        val session = subject.current()
        assertEquals("cks_xyz", session?.sessionId)
        assertEquals(created, session?.createdAt)
    }

    @Test
    fun clearRemovesSession() {
        val subject = AbandonedSessionStore(FakeKeyValueStore())
        subject.record("https://checkout.dodopayments.com/session/cks_xyz")
        subject.clear()
        assertNull(subject.current())
    }

    /**
     * The reported bug: pay, then tap the tab's ✕ while the hosted success page
     * is still counting down its redirect. The SDK reports CANCELLED because it
     * never saw the return URL, so the session must stay on record — that id is
     * the merchant's only handle for reconciling a payment that did go through.
     */
    @Test
    fun cancelledKeepsSessionForReconciliation() {
        val subject = AbandonedSessionStore(FakeKeyValueStore())
        subject.record("https://checkout.dodopayments.com/session/cks_xyz")
        subject.clearIfOutcomeKnown(CheckoutStatus.CANCELLED)
        assertEquals("cks_xyz", subject.current()?.sessionId)
    }

    @Test
    fun resolvedOutcomesClearSession() {
        for (status in CheckoutStatus.entries - CheckoutStatus.CANCELLED) {
            val subject = AbandonedSessionStore(FakeKeyValueStore())
            subject.record("https://checkout.dodopayments.com/session/cks_xyz")
            subject.clearIfOutcomeKnown(status)
            assertNull("$status should clear the record", subject.current())
        }
    }

    @Test
    fun noSessionReturnsNull() {
        assertNull(AbandonedSessionStore(FakeKeyValueStore()).current())
    }

    @Test
    fun urlWithoutSessionSegmentRecordsNothing() {
        val store = FakeKeyValueStore()
        val subject = AbandonedSessionStore(store)
        subject.record("https://checkout.dodopayments.com/whatever")
        assertNull(subject.current())
        assertEquals(emptyMap<String, String>(), store.values)
    }
}
