# Dodo Payments Checkout React Native SDK

Open Dodo Payments' hosted checkout in a system browser tab
(`SFSafariViewController` on iOS, a Custom Tab on Android) from React Native
and get a clean result from one call. Thin wrapper over the native
iOS/Android cores — no checkout logic of its own.

Turbo Module, **New Architecture only**, React Native 0.77+. Requirements:
iOS 16+, Android minSdk 24.

> **Note:** Your Android app must build with `compileSdk` 34 or higher —
> that is the floor declared in the AAR metadata of the AndroidX artifacts
> the native core depends on (`androidx.activity`, `androidx.browser`,
> `androidx.core`). This module's own `compileSdkVersion` defaults to 35 and
> follows your project's `rootProject.ext.compileSdkVersion` when set. There
> is no Kotlin Gradle plugin or AGP floor beyond what your React Native
> version already imposes — RN 0.79's defaults (Kotlin 2.0.21 / AGP 8.8.2)
> are sufficient.

## Install

```sh
npm i @dodopayments/react-native-checkout
```

- **Android:** autolinked. Pulls `com.dodopayments.api:checkout-android` from Maven.
- **iOS:** `cd ios && pod install`. The Swift core is bundled in the package.
- **Expo:** development builds only (not Expo Go). Pass your callback scheme to
  the config plugin so prebuild wires Android + iOS:
  ```json
  {
    "expo": {
      "scheme": "myapp",
      "plugins": [
        [
          "@dodopayments/react-native-checkout",
          { "scheme": "myappcheckout" }
        ]
      ]
    }
  }
  ```
  Then run `npx expo prebuild` (or rebuild a dev client). The scheme must match
  `returnUrl` (e.g. `myappcheckout://return`) and must differ from `expo.scheme`,
  which Expo already registers on MainActivity.

## Setup

Register a callback URL scheme so the OS routes the checkout return back to
your app. On **Expo**, the config plugin above does this for both platforms —
you only still need the iOS `Linking` forwarder in JS (see Use).

On **bare React Native**:

- **iOS:** add a URL type for your scheme in Info.plist, and forward incoming
  URLs from your app's own `Linking` handling into
  `DodoCheckout.handleOpenURL(url)` — `SFSafariViewController` has no
  in-process way to catch its own return URL.
- **Android:** set your callback scheme as a Gradle manifest placeholder — the
  underlying Android core's own manifest already declares the redirect
  activity's intent-filter, so this one property is the entire setup cost:

  ```kotlin
  android {
      defaultConfig {
          manifestPlaceholders["dodoCallbackScheme"] = "myappcheckout"
      }
  }
  ```

## Use

```ts
import { Linking } from 'react-native';
import { DodoCheckout } from '@dodopayments/react-native-checkout';

// Required for iOS's return-URL handling.
Linking.addEventListener('url', ({ url }) => DodoCheckout.handleOpenURL(url));

const result = await DodoCheckout.start({
  checkoutUrl,                          // from your backend
  returnUrl: 'myappcheckout://return', // scheme must be registered (see Setup)
  onEvent: (e) => console.log(e.type),  // logging only — never decide outcome from events
});

switch (result.status) {
  case 'succeeded': /* UI only — confirm server-side */ break;
  case 'failed':    break;
  case 'cancelled': await reconcileAbandonedSession(); break; // outcome unknown — NOT a failure
  case 'pending':   await reconcileAbandonedSession(); break; // may be unparsed, not just async
  case 'expired':   break;
}
```

## Result is a UI hint, not proof of payment

This SDK never calls the Dodo API and holds no API key. Grant access on your
backend from the webhook (`payment.succeeded` / `subscription.active`).

## Verify the payment

Confirm every payment from your backend, not from the mobile result:

- **Webhook**: Dodo Payments calls your backend when a payment
  succeeds or a subscription activates. Check the
  [Webhooks guide](https://docs.dodopayments.com/developer-resources/webhooks).
- **Verification API**: look up `paymentId` with your secret key via
  [Get Payment Detail](https://docs.dodopayments.com/api-reference/payments/get-payments-1).

## `cancelled` is not a failure

`cancelled` means the user dismissed the browser before any return URL arrived,
so the SDK never learned the outcome. **The payment may have gone through.** A
user who pays and then taps ✕ while the hosted "Payment Successful" page counts
down its redirect produces `cancelled`, and is indistinguishable, from the
SDK's side, from a user who closed the browser without paying.

Showing "Payment failed" here tells a paying customer their money vanished.
Resolve it instead: the SDK keeps the session on record for exactly this case.

## Abandoned sessions

A session stays on record whenever the SDK never saw a return URL it could
resolve to a durable outcome — the app (or the JS bundle) was killed
mid-checkout and the promise was lost, `start` resolved `cancelled`, or it
resolved `pending` from an unparseable return URL rather than a genuinely async
payment method. Reconcile it server-side, both on next mount and right after a
`cancelled` or `pending` result:

```ts
import { DodoCheckout } from '@dodopayments/react-native-checkout';

async function reconcileAbandonedSession() {
  const abandoned = await DodoCheckout.getAbandonedSession();
  if (!abandoned) return; // nothing in flight

  // Ask *your* backend what happened to abandoned.sessionId — it has the
  // webhook (`payment.succeeded`) or can call Get Payment Detail with your
  // secret key. Show a spinner while you wait; an async method may still be
  // settling, so treat "no record yet" as pending, not failed — and only
  // clear the record once you have a terminal outcome, or a later retry
  // has nothing left to reconcile against if this one comes back.
  const outcome = await myBackend.outcomeForSession(abandoned.sessionId);
  if (outcome.isTerminal) {
    await DodoCheckout.clearAbandonedSession();
  }
  show(outcome);
}
```

## Customization

The checkout browser's toolbar, buttons, and color scheme can be customized via `customization` on `start(...)`. See the [Appearance Customization docs](https://docs.dodopayments.com/developer-resources/sdks/react-native#appearance-customization) for all available options.

## Errors

`start` rejects with a `CheckoutError` (`.code` is one of `INVALID_CHECKOUT_URL`,
`INVALID_RETURN_URL`, `ALREADY_IN_PROGRESS`, `PLATFORM_ERROR`) only for misuse
or platform failure. A cancel or a declined payment is a **result**
(`cancelled` / `failed`), never a rejection.

## Local development

```sh
./scripts/sync-ios-core.sh   # vendor ../swift into ios/DodoCore for the pod
npm run typecheck && npm test
```
