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
