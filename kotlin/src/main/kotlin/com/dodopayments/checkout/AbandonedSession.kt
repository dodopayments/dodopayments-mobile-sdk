package com.dodopayments.checkout

/**
 * A checkout that ended without the SDK ever seeing its return URL — the app
 * was killed mid-flow, or the user dismissed the Custom Tab.
 *
 * The SDK never learns the payment's real outcome in either case: it holds no
 * API key and reads the result off the return URL, which never arrived. The
 * merchant reconciles the session server-side (webhook or `payments.retrieve`).
 * This record only tells the app *that* a checkout was interrupted, and which
 * session it was.
 */
data class AbandonedSession(
    val sessionId: String,
    /** Epoch milliseconds at which the checkout was started. */
    val createdAt: Long
)

/**
 * Minimal key/value persistence, so the store can be tested without touching
 * real SharedPreferences.
 */
internal interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/**
 * Records the in-flight session so it survives process death, and clears it on
 * a clean finish. Stores just enough to identify the session for reconciliation.
 */
internal class AbandonedSessionStore(private val store: KeyValueStore) {

    fun record(checkoutUrl: String, atMillis: Long = System.currentTimeMillis()) {
        val sessionId = sessionIdFrom(checkoutUrl) ?: return
        store.put(SESSION_KEY, sessionId)
        store.put(CREATED_AT_KEY, atMillis.toString())
    }

    fun current(): AbandonedSession? {
        val sessionId = store.get(SESSION_KEY)?.takeIf { it.isNotEmpty() } ?: return null
        val createdAt = store.get(CREATED_AT_KEY)?.toLongOrNull() ?: 0L
        return AbandonedSession(sessionId, createdAt)
    }

    fun clear() {
        store.remove(SESSION_KEY)
        store.remove(CREATED_AT_KEY)
    }

    /**
     * Clears the record only when the checkout produced a *durable* outcome.
     *
     * CANCELLED means the user dismissed the tab before any return URL
     * arrived, so the SDK learned nothing — the payment may well have
     * succeeded (dismissing while the hosted "Payment Successful" page counts
     * down its redirect is indistinguishable, from here, from dismissing
     * before paying at all). PENDING is the same kind of non-answer: it's also
     * [ResultParser]'s fallback for a missing or unrecognized `status`, so a
     * malformed return URL lands here too, with `paymentId` and
     * `subscriptionId` both potentially null — clearing then would leave no
     * handle at all, which is worse than the bug this method exists to fix.
     * An exhaustive `when` rather than a CANCELLED-only guard, so a future
     * status is a compile error here instead of silently falling through to
     * "clear".
     */
    fun clearIfOutcomeKnown(status: CheckoutStatus) {
        when (status) {
            CheckoutStatus.SUCCEEDED, CheckoutStatus.FAILED, CheckoutStatus.EXPIRED -> clear()
            CheckoutStatus.CANCELLED, CheckoutStatus.PENDING -> Unit
        }
    }

    companion object {
        private const val SESSION_KEY = "com.dodopayments.checkout.abandoned.sessionId"
        private const val CREATED_AT_KEY = "com.dodopayments.checkout.abandoned.createdAt"

        /** Extracts the `cks_…` session id from a checkout URL's `/session/{id}` path. */
        fun sessionIdFrom(checkoutUrl: String): String? {
            val path = UrlParts.from(checkoutUrl).path ?: return null
            val parts = path.split("/").filter { it.isNotEmpty() }
            val idx = parts.indexOf("session")
            return if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1] else null
        }
    }
}
