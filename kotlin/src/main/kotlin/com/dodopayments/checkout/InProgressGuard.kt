package com.dodopayments.checkout

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enforces the "only one checkout at a time" rule. A second [begin] while a
 * checkout is live throws [CheckoutError.Code.ALREADY_IN_PROGRESS].
 */
internal class InProgressGuard {
    private val inProgress = AtomicBoolean(false)

    fun begin() {
        if (!inProgress.compareAndSet(false, true)) {
            throw CheckoutError(
                CheckoutError.Code.ALREADY_IN_PROGRESS,
                "A checkout is already in progress. Only one can run at a time."
            )
        }
    }

    fun end() {
        inProgress.set(false)
    }
}
