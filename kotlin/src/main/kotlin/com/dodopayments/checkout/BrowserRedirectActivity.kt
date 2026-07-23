package com.dodopayments.checkout

import android.app.Activity
import android.os.Bundle

/**
 * Catches the browser's navigation to the merchant `return_url` scheme and
 * forwards it to [BrowserCheckoutHostActivity], then finishes itself so it
 * never lingers in the back stack.
 *
 * The library's own manifest already declares this activity's intent-filter,
 * using `${dodoCallbackScheme}` as the scheme. Set that placeholder in your
 * own app's `build.gradle`:
 *
 * ```kotlin
 * android {
 *     defaultConfig {
 *         manifestPlaceholders["dodoCallbackScheme"] = "myapp"
 *     }
 * }
 * ```
 *
 * That one Gradle property is the entire merchant setup cost — no manifest
 * XML to hand-copy. `myapp` here must match the scheme used in
 * `CheckoutParams.returnUrl` (e.g. `myapp://checkout/return`). If it's ever
 * left unset, the build fails immediately with an unresolved-placeholder
 * error rather than silently failing at checkout time; if it's set but
 * doesn't match `returnUrl`'s scheme, [DodoCheckout.start] throws a clear
 * `PLATFORM_ERROR` before presenting anything (see `RedirectResolution`).
 */
internal class BrowserRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { redirectUri ->
            startActivity(BrowserCheckoutHostActivity.redirectIntent(this, redirectUri))
        }
        finish()
    }
}
