# Dodo Payments Checkout Android SDK

Open Dodo Payments' hosted checkout in a Custom Tab (`androidx.browser.customtabs`)
and get a clean result from one call.

## Install

```kotlin
dependencies {
    implementation("com.dodopayments.api:checkout-android:1.1.0")
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
            CheckoutStatus.CANCELLED -> reconcileAbandonedSession()  // outcome unknown — NOT a failure
            CheckoutStatus.PENDING -> reconcileAbandonedSession()   // may be unparsed, not just async
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
        CheckoutStatus.CANCELLED -> reconcileAbandonedSession()  // outcome unknown — NOT a failure
        CheckoutStatus.PENDING -> reconcileAbandonedSession()   // may be unparsed, not just async
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

## `CANCELLED` is not a failure

`CANCELLED` means the user dismissed the tab before any return URL arrived, so
the SDK never learned the outcome. **The payment may have gone through.** A
user who pays and then taps ✕ while the hosted "Payment Successful" page counts
down its redirect produces `CANCELLED`, and is indistinguishable, from the
SDK's side, from a user who closed the tab without paying.

Showing "Payment failed" here tells a paying customer their money vanished.
Resolve it instead: the SDK keeps the session on record for exactly this case.

## Abandoned sessions

A session stays on record whenever the SDK never saw a return URL it could
resolve to a durable outcome — the app was killed mid-checkout, the result
came back `CANCELLED`, or it came back `PENDING` from an unparseable return
URL rather than a genuinely async payment method. Reconcile it server-side,
both on next launch and right after a `CANCELLED` or `PENDING` result:

```kotlin
import com.dodopayments.checkout.DodoCheckout

suspend fun reconcileAbandonedSession() {
    val abandoned = DodoCheckout.getAbandonedSession(context)
    if (abandoned == null) {
        dismiss()   // nothing in flight
        return
    }
    // Ask *your* backend what happened to abandoned.sessionId — it has the
    // webhook (`payment.succeeded`) or can call Get Payment Detail with your
    // secret key. Show a spinner while you wait; an async method may still be
    // settling, so treat "no record yet" as pending, not failed — and only
    // clear the record once you have a terminal outcome, or a later retry
    // has nothing left to reconcile against if this one comes back.
    val outcome = myBackend.outcomeForSession(abandoned.sessionId)
    if (outcome.isTerminal) {
        DodoCheckout.clearAbandonedSession(context)
    }
    show(outcome)
}
```

## Customization

The Custom Tab's toolbar, buttons, and color scheme can be customized via `customization` on `CheckoutParams`. See the [Appearance Customization docs](https://docs.dodopayments.com/developer-resources/sdks/android#appearance-customization) for all available options.

## Errors

`start` throws `CheckoutError` only for misuse or platform failure:
`INVALID_CHECKOUT_URL`, `INVALID_RETURN_URL`, `ALREADY_IN_PROGRESS`,
`PLATFORM_ERROR`. A user cancelling or a declined payment is a **result**
(`CANCELLED` / `FAILED`), never a thrown error.

With the launcher-style contract, validation errors throw out of
`launcher.launch(...)`.
