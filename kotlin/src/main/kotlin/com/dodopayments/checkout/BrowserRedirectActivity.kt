package com.dodopayments.checkout

import android.app.Activity
import android.os.Bundle

/**
 * Catches the browser's navigation to the merchant `return_url` scheme and
 * forwards it to [BrowserCheckoutHostActivity], then finishes itself so it
 * never lingers in the back stack.
 *
 * This library's own manifest declares this activity with **no**
 * intent-filter — the redirect scheme is chosen per merchant, and there is no
 * safe library-wide default. Redeclare this same activity by its
 * fully-qualified name in your own app's `AndroidManifest.xml` with your
 * scheme's intent-filter; AGP's manifest merger unions it with the library's
 * declaration:
 *
 * ```xml
 * <activity
 *     android:name="com.dodopayments.checkout.BrowserRedirectActivity"
 *     android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.intent.action.VIEW" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *         <category android:name="android.intent.category.BROWSABLE" />
 *         <data android:scheme="myapp" />
 *     </intent-filter>
 * </activity>
 * ```
 *
 * That one block is the entire merchant setup cost. `myapp` here must match
 * the scheme used in `CheckoutParams.returnUrl` (e.g. `myapp://checkout/return`).
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
