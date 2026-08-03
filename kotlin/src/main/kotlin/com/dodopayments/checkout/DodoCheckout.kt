package com.dodopayments.checkout

import android.app.Activity
import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.CompletableDeferred

/**
 * Entry point for the Dodo Payments mobile checkout.
 *
 * Open Dodo's hosted checkout in a Custom Tab and get a clean result from one
 * call:
 *
 * ```kotlin
 * val result = DodoCheckout.start(
 *     activity,
 *     CheckoutParams(
 *         checkoutUrl = checkoutUrl, // from your backend
 *         returnUrl = "myapp://checkout/return"
 *     )
 * )
 * ```
 *
 * The SDK contains **zero networking code** and holds **no API key**. It
 * watches navigation for your `return_url` and parses the result off the
 * query string. The result is a UI hint — grant access server-side from the
 * webhook.
 */
object DodoCheckout {

    /**
     * Presents Dodo's hosted checkout in a Custom Tab and resolves with the
     * outcome once the checkout navigates to `returnUrl` (or the user closes
     * the tab).
     *
     * @param activity The activity to launch the checkout screen from.
     * @param params Checkout URL and return URL.
     * @param onEvent Lifecycle callback for logging/analytics only — never
     *   decide the outcome from events. (Suspend-style only; the launcher-style
     *   [contract] path does not emit events.)
     * @return A [CheckoutResult] (UI hint — not proof of payment).
     * @throws CheckoutError for invalid input, a concurrent checkout, or a
     *   platform failure. A cancel or a declined payment is a *result*, not a
     *   thrown error.
     */
    suspend fun start(
        activity: Activity,
        params: CheckoutParams,
        onEvent: ((CheckoutEvent) -> Unit)? = null
    ): CheckoutResult {
        // Validate before touching any UI.
        UrlValidator.validateCheckoutUrl(params.checkoutUrl)
        UrlValidator.validateReturnUrl(params.returnUrl)
        RedirectResolution.ensureResolvable(activity, params.returnUrl)

        CheckoutCoordinator.guard.begin()

        val store = AbandonedSessionStore(SharedPreferencesKeyValueStore(activity))
        val deferred = CompletableDeferred<CheckoutResult>()
        CheckoutCoordinator.pendingResult = deferred
        CheckoutCoordinator.onEvent = onEvent

        try {
            // Record the session so it survives process death; the checkout
            // activity clears it on a clean finish.
            store.record(params.checkoutUrl)
            activity.startActivity(BrowserCheckoutHostActivity.newIntent(activity, params))
        } catch (t: Throwable) {
            CheckoutCoordinator.clearPending()
            CheckoutCoordinator.guard.end()
            store.clear()
            throw if (t is CheckoutError) {
                t
            } else {
                CheckoutError(
                    CheckoutError.Code.PLATFORM_ERROR,
                    t.message ?: "Failed to launch the checkout screen."
                )
            }
        }

        return try {
            deferred.await()
        } finally {
            CheckoutCoordinator.clearPending()
        }
    }

    /**
     * Launcher-style alternative to [start]:
     *
     * ```kotlin
     * val launcher = registerForActivityResult(DodoCheckout.contract()) { result -> … }
     * launcher.launch(CheckoutParams(checkoutUrl, returnUrl))
     * ```
     *
     * Same behavior. Validation errors throw [CheckoutError] out of
     * `launcher.launch(...)`. This path survives process death: if Android
     * kills your app during an external payment-app hop, the result is still
     * delivered to the re-registered callback.
     */
    fun contract(): ActivityResultContract<CheckoutParams, CheckoutResult> = CheckoutContract()

    /**
     * The session of a checkout that ended without a confirmed outcome, or
     * `null`.
     *
     * Set whenever the SDK never saw the return URL — the app was killed
     * mid-flow, or the result came back [CheckoutStatus.CANCELLED] because the
     * user dismissed the tab. Also set for [CheckoutStatus.PENDING], since
     * that status can itself mean an unparseable return URL rather than a
     * genuinely async payment method. Check it on launch *and* after every
     * CANCELLED or PENDING result, reconcile the session server-side, then
     * call [clearAbandonedSession].
     */
    fun getAbandonedSession(context: Context): AbandonedSession? =
        AbandonedSessionStore(SharedPreferencesKeyValueStore(context)).current()

    /** Clears the abandoned-session record after the merchant has reconciled it. */
    fun clearAbandonedSession(context: Context) {
        AbandonedSessionStore(SharedPreferencesKeyValueStore(context)).clear()
    }
}
