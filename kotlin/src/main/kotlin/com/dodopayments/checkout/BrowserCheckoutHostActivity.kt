package com.dodopayments.checkout

import android.app.Activity
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
    private lateinit var returnUrl: String
    private lateinit var matcher: ReturnUrlMatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browserLaunched = savedInstanceState?.getBoolean(STATE_BROWSER_LAUNCHED) ?: false
        pausedSinceLaunch = savedInstanceState?.getBoolean(STATE_PAUSED_SINCE_LAUNCH) ?: false

        val checkoutUrl = savedInstanceState?.getString(STATE_CHECKOUT_URL)
            ?: intent.getStringExtra(EXTRA_CHECKOUT_URL)
        val returnUrl = savedInstanceState?.getString(STATE_RETURN_URL)
            ?: intent.getStringExtra(EXTRA_RETURN_URL)
        if (checkoutUrl.isNullOrEmpty() || returnUrl.isNullOrEmpty()) {
            failWith(
                CheckoutError(
                    CheckoutError.Code.PLATFORM_ERROR,
                    "Checkout was launched without its parameters."
                )
            )
            return
        }
        this.checkoutUrl = checkoutUrl
        this.returnUrl = returnUrl
        this.matcher = ReturnUrlMatcher(returnUrl)

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

    override fun onDestroy() {
        super.onDestroy()
        // isFinishing, not just "!isChangingConfigurations" — a config change
        // AND an ordinary memory-reclaim destroy (backgrounded, OS frees this
        // activity to recreate later from onSaveInstanceState, no process
        // restart involved) both leave isFinishing false, and both expect
        // this checkout to still be resumed later by a recreated instance.
        // Only isFinishing means the system tore this down for good (e.g.
        // the user swiped the task away from Recents) with no recreation
        // coming — that's the only case safe to resolve here. Getting this
        // wrong the other way is worse than the leak it fixes: completing
        // CheckoutCoordinator.pendingResult (a process-wide singleton) on a
        // reclaim-and-resume destroy would hand the suspend-style caller a
        // premature CANCELLED before the real outcome arrives on the
        // recreated instance, which then has nowhere left to deliver it.
        // Deliberately NOT cleanUpSession()/deliver() here either — those
        // clear the abandoned-session record, and this is exactly the
        // scenario that record exists to survive; the merchant reconciles it
        // via getAbandonedSession() on next launch.
        if (!delivered && isFinishing) {
            delivered = true
            CheckoutCoordinator.guard.end()
            CheckoutCoordinator.emit(CheckoutEvent.Closed)
            val result = CheckoutResult(CheckoutStatus.CANCELLED)
            setResult(Activity.RESULT_OK, ResultCodec.encode(result))
            CheckoutCoordinator.pendingResult?.complete(result)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_BROWSER_LAUNCHED, browserLaunched)
        outState.putBoolean(STATE_PAUSED_SINCE_LAUNCH, pausedSinceLaunch)
        if (::checkoutUrl.isInitialized) {
            outState.putString(STATE_CHECKOUT_URL, checkoutUrl)
        }
        if (::returnUrl.isInitialized) {
            outState.putString(STATE_RETURN_URL, returnUrl)
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
        // The manifest intent-filter only constrains the scheme, so anything
        // registered on it could send a redirect here. Require the full
        // scheme+host+path match before parsing it into a result — never trust
        // an unmatched URL as the real return. We early-return without
        // delivering here, but the forwarding intent has already brought this
        // activity to the foreground, so the ensuing onResume (browserLaunched
        // && pausedSinceLaunch && !delivered) resolves the checkout as
        // CANCELLED. The security property holds either way: an untrusted URL
        // is never parsed into a success/failure outcome.
        if (!matcher.matches(redirectUri)) return
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
        // Carries the result back to the launcher-style contract path via
        // onActivityResult; the suspend-style path below ignores this and
        // uses the in-memory deferred instead.
        setResult(Activity.RESULT_OK, ResultCodec.encode(result))
        CheckoutCoordinator.pendingResult?.complete(result)
        finish()
    }

    private fun failWith(error: CheckoutError) {
        if (delivered) return
        delivered = true
        cleanUpSession()
        CheckoutCoordinator.emit(CheckoutEvent.Closed)
        setResult(Activity.RESULT_OK, ResultCodec.encodeError(error))
        CheckoutCoordinator.pendingResult?.completeExceptionally(error)
        finish()
    }

    private fun cleanUpSession() {
        AbandonedSessionStore(SharedPreferencesKeyValueStore(this)).clear()
        CheckoutCoordinator.guard.end()
    }

    companion object {
        private const val EXTRA_CHECKOUT_URL = "com.dodopayments.checkout.extra.browserCheckoutUrl"
        private const val EXTRA_RETURN_URL = "com.dodopayments.checkout.extra.browserReturnUrl"
        internal const val EXTRA_REDIRECT_URI = "com.dodopayments.checkout.extra.browserRedirectUri"
        private const val STATE_BROWSER_LAUNCHED = "com.dodopayments.checkout.state.browserLaunched"
        private const val STATE_PAUSED_SINCE_LAUNCH = "com.dodopayments.checkout.state.pausedSinceLaunch"
        private const val STATE_CHECKOUT_URL = "com.dodopayments.checkout.state.browserCheckoutUrl"
        private const val STATE_RETURN_URL = "com.dodopayments.checkout.state.browserReturnUrl"

        fun newIntent(context: Context, params: CheckoutParams): Intent =
            Intent(context, BrowserCheckoutHostActivity::class.java).apply {
                putExtra(EXTRA_CHECKOUT_URL, params.checkoutUrl)
                putExtra(EXTRA_RETURN_URL, params.returnUrl)
            }

        /** Built by [BrowserRedirectActivity] to forward the caught redirect. */
        internal fun redirectIntent(context: Context, redirectUri: String): Intent =
            Intent(context, BrowserCheckoutHostActivity::class.java).apply {
                putExtra(EXTRA_REDIRECT_URI, redirectUri)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
    }
}
