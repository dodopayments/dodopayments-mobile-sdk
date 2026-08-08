package com.dodopayments.checkout

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
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
    private var customization: BrowserCustomization = BrowserCustomization()

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
                ),
                // Unlike the launchBrowser() catch below, this instance can be
                // a fresh recreation reached only by redirectIntent() after the
                // process died — it never had a checkoutUrl to lose, but an
                // earlier instance may have already shown a real checkout and
                // recorded its session. Clearing here would destroy that
                // record based on this instance's own confusion, not on any
                // evidence the checkout never displayed.
                clearsAbandonedSession = false
            )
            return
        }
        this.checkoutUrl = checkoutUrl
        this.returnUrl = returnUrl
        this.matcher = ReturnUrlMatcher(returnUrl)
        this.customization = (savedInstanceState?.getBundle(STATE_BROWSER_CUSTOMIZATION)
            ?: intent.getBundleExtra(EXTRA_BROWSER_CUSTOMIZATION))
            ?.toStringMap()
            ?.toBrowserCustomization()
            ?: BrowserCustomization()

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
        // The abandoned-session record is deliberately left in place, as it is
        // for every CANCELLED (and PENDING) outcome: this is exactly the
        // scenario that record exists to survive, and the merchant reconciles
        // it via getAbandonedSession() on next launch.
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
        outState.putBundle(STATE_BROWSER_CUSTOMIZATION, customization.toStringMap().toBundle())
    }

    private fun launchBrowser() {
        browserLaunched = true
        try {
            CustomTabsIntent.Builder()
                .apply { applyBrowserCustomization(customization) }
                .build()
                .launchUrl(this, Uri.parse(checkoutUrl))
        } catch (t: Throwable) {
            failWith(
                CheckoutError(
                    CheckoutError.Code.PLATFORM_ERROR,
                    t.message ?: "No browser available to open the checkout."
                )
            )
        }
    }

    // Every field is applied conditionally — `null` means the corresponding
    // setter is never called at all, so the Custom Tab host's own live
    // default applies, rather than this SDK asserting a value on its behalf.
    private fun CustomTabsIntent.Builder.applyBrowserCustomization(customization: BrowserCustomization) {
        if (customization.toolbarColor != null ||
            customization.secondaryToolbarColor != null ||
            customization.navigationBarColor != null ||
            customization.navigationBarDividerColor != null
        ) {
            val colorSchemeParams = CustomTabColorSchemeParams.Builder().apply {
                customization.toolbarColor?.let { setToolbarColor(it) }
                customization.secondaryToolbarColor?.let { setSecondaryToolbarColor(it) }
                customization.navigationBarColor?.let { setNavigationBarColor(it) }
                customization.navigationBarDividerColor?.let { setNavigationBarDividerColor(it) }
            }.build()
            setDefaultColorSchemeParams(colorSchemeParams)
        }
        customization.colorScheme?.let {
            setColorScheme(
                when (it) {
                    BrowserCustomization.ColorScheme.LIGHT -> CustomTabsIntent.COLOR_SCHEME_LIGHT
                    BrowserCustomization.ColorScheme.DARK -> CustomTabsIntent.COLOR_SCHEME_DARK
                    BrowserCustomization.ColorScheme.SYSTEM -> CustomTabsIntent.COLOR_SCHEME_SYSTEM
                }
            )
        }
        customization.showTitleEnabled?.let { setShowTitle(it) }
        customization.urlBarHidingEnabled?.let { setUrlBarHidingEnabled(it) }
        customization.shareButtonEnabled?.let {
            setShareState(if (it) CustomTabsIntent.SHARE_STATE_ON else CustomTabsIntent.SHARE_STATE_OFF)
        }
        customization.bookmarksButtonEnabled?.let { setBookmarksButtonEnabled(it) }
        customization.downloadsButtonEnabled?.let { setDownloadButtonEnabled(it) }
        customization.closeButtonPosition?.let {
            setCloseButtonPosition(
                if (it == BrowserCustomization.CloseButtonPosition.END) CustomTabsIntent.CLOSE_BUTTON_POSITION_END
                else CustomTabsIntent.CLOSE_BUTTON_POSITION_START
            )
        }
        if (customization.closeButtonStyle == BrowserCustomization.CloseButtonStyle.BACK) {
            setCloseButtonIcon(CloseButtonIcons.backArrow(this@BrowserCheckoutHostActivity, customization.toolbarColor))
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
        // Kept on CANCELLED and PENDING — see clearIfOutcomeKnown. Those are
        // the outcomes the SDK cannot fully vouch for, and the ones the
        // merchant still has to reconcile.
        AbandonedSessionStore(SharedPreferencesKeyValueStore(this))
            .clearIfOutcomeKnown(result.status)
        CheckoutCoordinator.guard.end()
        // Emit Closed before completing the deferred, since completion can
        // resume the awaiting caller synchronously and its `finally` nulls
        // `onEvent`.
        CheckoutCoordinator.emit(CheckoutEvent.Closed)
        // Carries the result back to the launcher-style contract path via
        // onActivityResult; the suspend-style path below ignores this and
        // uses the in-memory deferred instead.
        setResult(Activity.RESULT_OK, ResultCodec.encode(result))
        CheckoutCoordinator.pendingResult?.complete(result)
        finish()
    }

    private fun failWith(error: CheckoutError, clearsAbandonedSession: Boolean = true) {
        if (delivered) return
        delivered = true
        // Only the no-browser-installed path (launchBrowser's catch) can prove
        // the checkout page never loaded, so only that call site clears here by
        // default. The missing-parameters path opts out — see its call site.
        if (clearsAbandonedSession) {
            AbandonedSessionStore(SharedPreferencesKeyValueStore(this)).clear()
        }
        CheckoutCoordinator.guard.end()
        CheckoutCoordinator.emit(CheckoutEvent.Closed)
        setResult(Activity.RESULT_OK, ResultCodec.encodeError(error))
        CheckoutCoordinator.pendingResult?.completeExceptionally(error)
        finish()
    }

    companion object {
        private const val EXTRA_CHECKOUT_URL = "com.dodopayments.checkout.extra.browserCheckoutUrl"
        private const val EXTRA_RETURN_URL = "com.dodopayments.checkout.extra.browserReturnUrl"
        internal const val EXTRA_REDIRECT_URI = "com.dodopayments.checkout.extra.browserRedirectUri"
        private const val STATE_BROWSER_LAUNCHED = "com.dodopayments.checkout.state.browserLaunched"
        private const val STATE_PAUSED_SINCE_LAUNCH = "com.dodopayments.checkout.state.pausedSinceLaunch"
        private const val STATE_CHECKOUT_URL = "com.dodopayments.checkout.state.browserCheckoutUrl"
        private const val STATE_RETURN_URL = "com.dodopayments.checkout.state.browserReturnUrl"
        private const val EXTRA_BROWSER_CUSTOMIZATION = "com.dodopayments.checkout.extra.browserCustomization"
        private const val STATE_BROWSER_CUSTOMIZATION = "com.dodopayments.checkout.state.browserCustomization"

        fun newIntent(context: Context, params: CheckoutParams): Intent =
            Intent(context, BrowserCheckoutHostActivity::class.java).apply {
                putExtra(EXTRA_CHECKOUT_URL, params.checkoutUrl)
                putExtra(EXTRA_RETURN_URL, params.returnUrl)
                putExtra(EXTRA_BROWSER_CUSTOMIZATION, params.customization.toStringMap().toBundle())
            }

        // Bundle <-> Map adapter at the actual Intent/state boundary; the
        // field-level encoding itself lives in BrowserCustomization.kt so it
        // stays unit-testable without android.os.Bundle.
        private fun Bundle.toStringMap(): Map<String, String> =
            keySet().associateWith { getString(it) ?: "" }

        private fun Map<String, String>.toBundle(): Bundle =
            Bundle().apply { forEach { (key, value) -> putString(key, value) } }

        /** Built by [BrowserRedirectActivity] to forward the caught redirect. */
        internal fun redirectIntent(context: Context, redirectUri: String): Intent =
            Intent(context, BrowserCheckoutHostActivity::class.java).apply {
                putExtra(EXTRA_REDIRECT_URI, redirectUri)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
    }
}
