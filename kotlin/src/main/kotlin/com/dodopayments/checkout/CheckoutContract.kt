package com.dodopayments.checkout

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/**
 * Launcher-style entry point: `registerForActivityResult(DodoCheckout.contract())`.
 * Same behavior as the suspend-style [DodoCheckout.start].
 *
 * Validation errors and the single-checkout guard throw [CheckoutError] out of
 * `launcher.launch(params)` (from [createIntent]). Because this path survives
 * process death, it is the most robust way to receive the result if Android
 * kills your app during an external payment-app hop.
 */
internal class CheckoutContract : ActivityResultContract<CheckoutParams, CheckoutResult>() {

    override fun createIntent(context: Context, input: CheckoutParams): Intent {
        UrlValidator.validateCheckoutUrl(input.checkoutUrl)
        UrlValidator.validateReturnUrl(input.returnUrl)
        RedirectResolution.ensureResolvable(context, input.returnUrl)
        CheckoutCoordinator.guard.begin()
        AbandonedSessionStore(SharedPreferencesKeyValueStore(context)).record(input.checkoutUrl)
        return BrowserCheckoutHostActivity.newIntent(context, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): CheckoutResult {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            // The activity never delivered a result (e.g. it failed to launch), so
            // it never freed the guard itself. Release it here to avoid a
            // permanent ALREADY_IN_PROGRESS lock on this launcher path.
            // (A normal cancel/success returns RESULT_OK and the activity already
            // freed the guard, so this branch cannot clobber a live checkout.)
            CheckoutCoordinator.guard.end()
            return CheckoutResult(CheckoutStatus.CANCELLED)
        }
        // An activity-side failure (e.g. PLATFORM_ERROR) cannot be thrown
        // across the activity-result callback without crashing dispatch, so it
        // surfaces as CANCELLED with the error code in `raw["error"]`.
        ResultCodec.decodeError(intent)?.let { error ->
            return CheckoutResult(
                status = CheckoutStatus.CANCELLED,
                raw = mapOf("error" to error.code.name)
            )
        }
        return ResultCodec.decode(intent) ?: CheckoutResult(CheckoutStatus.CANCELLED)
    }
}
