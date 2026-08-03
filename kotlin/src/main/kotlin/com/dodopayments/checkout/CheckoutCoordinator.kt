package com.dodopayments.checkout

import kotlinx.coroutines.CompletableDeferred

/**
 * Process-wide state shared between [DodoCheckout] and [BrowserCheckoutHostActivity].
 *
 * The suspend-style [DodoCheckout.start] parks a deferred here that the
 * activity completes; the launcher-style contract path never uses it (results
 * flow back through `setResult`/`onActivityResult`, which also survives
 * process death). The single-checkout guard lives here too so both entry
 * points share it.
 */
internal object CheckoutCoordinator {

    val guard = InProgressGuard()

    /** Completed exactly once by the activity for the suspend-style path. */
    var pendingResult: CompletableDeferred<CheckoutResult>? = null

    /** Lifecycle event sink for the current checkout (logging only). */
    var onEvent: ((CheckoutEvent) -> Unit)? = null

    fun emit(event: CheckoutEvent) {
        onEvent?.invoke(event)
    }

    fun clearPending() {
        pendingResult = null
        onEvent = null
    }
}
