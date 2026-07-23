package com.dodopayments.checkout

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Verifies some activity in this app actually resolves `returnUrl`'s scheme
 * before presenting the Custom Tab, so a missing or mismatched
 * `manifestPlaceholders["dodoCallbackScheme"]` fails loudly here with an
 * actionable message instead of silently hanging after the browser redirects.
 */
internal object RedirectResolution {
    fun ensureResolvable(context: Context, returnUrl: String) {
        val scheme = UrlParts.from(returnUrl).scheme ?: return
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://dodo-checkout-probe"))
        val resolved = context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        val resolvesToThisApp = resolved.any {
            it.activityInfo?.packageName == context.packageName &&
                it.activityInfo?.name == BrowserRedirectActivity::class.java.name
        }
        if (!resolvesToThisApp) {
            throw CheckoutError(
                CheckoutError.Code.PLATFORM_ERROR,
                "No activity in this app resolves the '$scheme' scheme used by returnUrl. Set " +
                    "manifestPlaceholders[\"dodoCallbackScheme\"] = \"$scheme\" in your app's " +
                    "build.gradle so BrowserRedirectActivity can catch the checkout return."
            )
        }
    }
}
