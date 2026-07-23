package com.dodopayments.checkout

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent

/**
 * The SDK-owned host for the Custom Tab checkout surface. It launches
 * the checkout URL in a Custom Tab and resolves the result one of two ways:
 *  - [BrowserRedirectActivity] catches the browser's navigation to the
 *    merchant `return_url` scheme and forwards it here via [onNewIntent].
 *  - If the user backs out of the tab without a redirect, this activity's own
 *    [onResume] fires a second time with nothing forwarded — treated as a
 *    cancel. (Plain Custom Tabs, unlike an Auth Tab, has no dedicated
 *    cancel callback, so a resume-without-redirect is the signal.)
 */
internal class BrowserCheckoutHostActivity : ComponentActivity() {

    private var delivered = false
    private var browserLaunched = false
    private var pausedSinceLaunch = false
    private lateinit var checkoutUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browserLaunched = savedInstanceState?.getBoolean(STATE_BROWSER_LAUNCHED) ?: false
        pausedSinceLaunch = savedInstanceState?.getBoolean(STATE_PAUSED_SINCE_LAUNCH) ?: false

        val checkoutUrl = savedInstanceState?.getString(STATE_CHECKOUT_URL)
            ?: intent.getStringExtra(EXTRA_CHECKOUT_URL)
        if (checkoutUrl.isNullOrEmpty()) {
            failWith(
                CheckoutError(
                    CheckoutError.Code.PLATFORM_ERROR,
                    "Checkout was launched without its parameters."
                )
            )
            return
        }
        this.checkoutUrl = checkoutUrl

        if (savedInstanceState == null) {
            CheckoutCoordinator.emit(CheckoutEvent.Opened)
        }

        handleRedirectIfPresent(intent)
        if (!browserLaunched && !delivered) {
            launchBrowser()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRedirectIfPresent(intent)
    }

    override fun onPause() {
        super.onPause()
        if (browserLaunched) {
            pausedSinceLaunch = true
        }
    }

    override fun onResume() {
        super.onResume()
        // onResume also fires once as part of this activity's own normal
        // startup (onCreate -> onStart -> onResume), *before* the Custom Tab
        // takes the foreground and pauses us — so that first resume must not
        // be mistaken for "returned from the tab". Only a resume that follows
        // an onPause recorded after the browser launched means the user is
        // back, with nothing forwarded by BrowserRedirectActivity — i.e. they
        // backed out of the tab manually.
        if (browserLaunched && pausedSinceLaunch && !delivered) {
            deliver(CheckoutResult(CheckoutStatus.CANCELLED))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_BROWSER_LAUNCHED, browserLaunched)
        outState.putBoolean(STATE_PAUSED_SINCE_LAUNCH, pausedSinceLaunch)
        if (::checkoutUrl.isInitialized) {
            outState.putString(STATE_CHECKOUT_URL, checkoutUrl)
        }
    }

    private fun launchBrowser() {
        browserLaunched = true
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(checkoutUrl))
        } catch (t: Throwable) {
            failWith(
                CheckoutError(
                    CheckoutError.Code.PLATFORM_ERROR,
                    t.message ?: "No browser available to open the checkout."
                )
            )
        }
    }

    private fun handleRedirectIfPresent(intent: Intent) {
        val redirectUri = intent.getStringExtra(EXTRA_REDIRECT_URI) ?: return
        deliver(ResultParser.parse(redirectUri))
    }

    // MARK: Finishing

    private fun deliver(result: CheckoutResult) {
        if (delivered) return
        delivered = true
        if (result.status != CheckoutStatus.CANCELLED) {
            CheckoutCoordinator.emit(CheckoutEvent.ReturnReceived)
        }
        cleanUpSession()
        // Same ordering as CheckoutActivity: emit Closed before completing the
        // deferred, since completion can resume the awaiting caller
        // synchronously and its `finally` nulls `onEvent`.
        CheckoutCoordinator.emit(CheckoutEvent.Closed)
        CheckoutCoordinator.pendingResult?.complete(result)
        finish()
    }

    private fun failWith(error: CheckoutError) {
        if (delivered) return
        delivered = true
        cleanUpSession()
        CheckoutCoordinator.emit(CheckoutEvent.Closed)
        CheckoutCoordinator.pendingResult?.completeExceptionally(error)
        finish()
    }

    private fun cleanUpSession() {
        AbandonedSessionStore(SharedPreferencesKeyValueStore(this)).clear()
        CheckoutCoordinator.guard.end()
    }

    companion object {
        private const val EXTRA_CHECKOUT_URL = "com.dodopayments.checkout.extra.browserCheckoutUrl"
        internal const val EXTRA_REDIRECT_URI = "com.dodopayments.checkout.extra.browserRedirectUri"
        private const val STATE_BROWSER_LAUNCHED = "com.dodopayments.checkout.state.browserLaunched"
        private const val STATE_PAUSED_SINCE_LAUNCH = "com.dodopayments.checkout.state.pausedSinceLaunch"
        private const val STATE_CHECKOUT_URL = "com.dodopayments.checkout.state.browserCheckoutUrl"

        fun newIntent(context: Context, params: CheckoutParams): Intent =
            Intent(context, BrowserCheckoutHostActivity::class.java).apply {
                putExtra(EXTRA_CHECKOUT_URL, params.checkoutUrl)
            }

        /** Built by [BrowserRedirectActivity] to forward the caught redirect. */
        internal fun redirectIntent(context: Context, redirectUri: String): Intent =
            Intent(context, BrowserCheckoutHostActivity::class.java).apply {
                putExtra(EXTRA_REDIRECT_URI, redirectUri)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
    }
}
