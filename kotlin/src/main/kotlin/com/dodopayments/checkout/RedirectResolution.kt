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
        // Lowercase the scheme so the probe matches the manifest intent-filter
        // regardless of casing. Android normalizes intent-filter schemes to
        // lowercase at match time, and ReturnUrlMatcher compares schemes
        // case-insensitively too, so an uppercase returnUrl scheme (e.g.
        // "MyApp://…") must still probe as "myapp" to stay consistent.
        val scheme = UrlParts.from(returnUrl).scheme?.lowercase() ?: return
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://dodo-checkout-probe"))
        val resolved = context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        // Match only this app's own BrowserRedirectActivity — not merely any
        // activity that resolves the scheme. Custom schemes aren't unique, so
        // another installed app could declare the same scheme; requiring both
        // this app's packageName and BrowserRedirectActivity's class name
        // avoids the false confidence that a device-wide scheme collision would
        // otherwise give.
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
