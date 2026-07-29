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
- **Expo:** development builds only (not Expo Go). Pass a **dedicated**
  callback scheme to the config plugin so prebuild wires Android + iOS:
  ```json
  {
    "expo": {
      "scheme": "myapp",
      "plugins": [
        [
          "@dodopayments/react-native-checkout",
          { "scheme": "myapp.checkout" }
        ]
      ]
    }
  }
  ```
  Then run `npx expo prebuild` (or rebuild a dev client). Use a scheme that is
  **not** the same as top-level `expo.scheme` — otherwise Android may register
  both MainActivity and the SDK redirect activity for the same scheme and the
  checkout return can land on the wrong activity. The plugin scheme must match
  `returnUrl` (e.g. `myapp.checkout://return`). Pass only the scheme token
  (not a full `https://…` URL).

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
          manifestPlaceholders["dodoCallbackScheme"] = "myapp.checkout"
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
  returnUrl: 'myapp.checkout://return', // scheme must be registered (see Setup)
  onEvent: (e) => console.log(e.type),  // logging only — never decide outcome from events
});

switch (result.status) {
  case 'succeeded': /* UI only — confirm server-side */ break;
  case 'failed':    break;
  case 'cancelled': break;
  case 'pending':   break; // settles later; webhook is authority
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

## Abandoned sessions

If the app (or the JS bundle) is killed mid-checkout the promise is lost, but
the native layer keeps the session. Recover it on next mount:

```ts
import { DodoCheckout } from '@dodopayments/react-native-checkout';

const abandoned = await DodoCheckout.getAbandonedSession();
if (abandoned) {
  // reconcile abandoned.sessionId server-side, then:
  await DodoCheckout.clearAbandonedSession();
}
```

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
