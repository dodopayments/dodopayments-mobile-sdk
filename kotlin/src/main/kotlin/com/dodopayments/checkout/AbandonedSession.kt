package com.dodopayments.checkout

/**
 * A checkout the app was killed or dismissed in the middle of.
 *
 * The SDK cannot know the payment's real outcome after the process dies — the
 * merchant reconciles it server-side (webhook or `payments.retrieve`). This
 * record only tells the app *that* a checkout was interrupted.
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
