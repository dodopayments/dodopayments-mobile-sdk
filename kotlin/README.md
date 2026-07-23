# Dodo Payments Checkout Android SDK

Open Dodo Payments' hosted checkout in a Custom Tab (`androidx.browser.customtabs`)
and get a clean result from one call.

## Install

```kotlin
dependencies {
    implementation("com.dodopayments:checkout-android:1.0.0")
}
```

Requirements: minSdk 23, Kotlin. Dependencies: `androidx.activity` +
`androidx.browser` + kotlinx-coroutines only. The SDK contains zero networking
code.

## Setup

Set your callback scheme as a Gradle manifest placeholder — the library's own
manifest already declares `BrowserRedirectActivity`'s intent-filter, so this
one property is the entire setup cost:

```kotlin
android {
    defaultConfig {
        manifestPlaceholders["dodoCallbackScheme"] = "myapp"
    }
}
```

`myapp` here must match the scheme used in `CheckoutParams.returnUrl` (e.g.
`myapp://checkout/return`). If you forget to set it, the build fails
immediately with an unresolved-placeholder error rather than silently
failing at checkout time; if you set it but it doesn't match `returnUrl`'s
scheme, `DodoCheckout.start` throws a clear `PLATFORM_ERROR` before
presenting anything.

## Use

Launcher style (recommended — survives process death, since it's delivered
through Android's own `ActivityResultRegistry`, not an in-memory coroutine):

```kotlin
import com.dodopayments.checkout.CheckoutParams
import com.dodopayments.checkout.CheckoutStatus
import com.dodopayments.checkout.DodoCheckout

private val checkoutLauncher =
    registerForActivityResult(DodoCheckout.contract()) { result ->
        when (result.status) {
            CheckoutStatus.SUCCEEDED -> showSuccess(result.paymentId) // UI only — confirm server-side
            CheckoutStatus.FAILED -> showFailure()
            CheckoutStatus.CANCELLED -> dismiss()
            CheckoutStatus.PENDING -> showPending()                   // settles later; webhook is authority
            CheckoutStatus.EXPIRED -> showExpired()
        }
    }

checkoutLauncher.launch(
    CheckoutParams(
        checkoutUrl = checkoutUrl, // from your backend's POST /checkouts
        returnUrl = "myapp://checkout/return"
    )
)
```

Suspend style:

```kotlin
import com.dodopayments.checkout.CheckoutParams
import com.dodopayments.checkout.CheckoutStatus
import com.dodopayments.checkout.DodoCheckout

lifecycleScope.launch {
    val result = DodoCheckout.start(
        activity = this@MyActivity,
        params = CheckoutParams(
            checkoutUrl = checkoutUrl, // from your backend's POST /checkouts
            returnUrl = "myapp://checkout/return"
        )
    )

    when (result.status) {
        CheckoutStatus.SUCCEEDED -> showSuccess(result.paymentId) // UI only — confirm server-side
        CheckoutStatus.FAILED -> showFailure()
        CheckoutStatus.CANCELLED -> dismiss()
        CheckoutStatus.PENDING -> showPending()                   // settles later; webhook is authority
        CheckoutStatus.EXPIRED -> showExpired()
    }
}
```

## What the result means

The result comes from the `return_url` query string. **It is a UI hint, not
proof of payment.** This SDK never calls the Dodo API and holds no API key.
Grant access on your backend from the webhook (`payment.succeeded` /
`subscription.active`) or by retrieving the payment with your secret key.

## Verify the payment

Confirm every payment from your backend, not from the mobile result:

- **Webhook**: Dodo Payments calls your backend when a payment
  succeeds or a subscription activates. Check the
  [Webhooks guide](https://docs.dodopayments.com/developer-resources/webhooks).
- **Verification API**: look up `paymentId` with your secret key via
  [Get Payment Detail](https://docs.dodopayments.com/api-reference/payments/get-payments-1).

## Abandoned sessions

If the app is killed mid-checkout, recover the interrupted session on next
launch and reconcile it server-side:

```kotlin
import com.dodopayments.checkout.DodoCheckout

DodoCheckout.getAbandonedSession(context)?.let { abandoned ->
    // reconcile abandoned.sessionId with your backend, then:
    DodoCheckout.clearAbandonedSession(context)
}
```

## Errors

`start` throws `CheckoutError` only for misuse or platform failure:
`INVALID_CHECKOUT_URL`, `INVALID_RETURN_URL`, `ALREADY_IN_PROGRESS`,
`PLATFORM_ERROR`. A user cancelling or a declined payment is a **result**
(`CANCELLED` / `FAILED`), never a thrown error.

With the launcher-style contract, validation errors throw out of
`launcher.launch(...)`.
