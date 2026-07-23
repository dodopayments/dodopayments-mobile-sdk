# Dodo Payments Mobile SDK

Native checkout SDKs for Dodo Payments' hosted checkout, for iOS, Android,
React Native, and Flutter.

Each SDK opens the checkout in a system browser surface — `SFSafariViewController`
on iOS, a Custom Tab (`androidx.browser.customtabs`) on Android — instead of an
in-app WebView. This means the checkout shares the device browser's existing
session, saved cards, autofill, and wallets (Apple Pay, Google Pay) exactly as
it would on the open web, and returns a clean result to your app from one
call.

## SDKs

| Platform | Package | Docs |
|---|---|---|
| iOS (Swift) | [Swift Package Manager](https://github.com/dodopayments/dodopayments-checkout-ios) | [swift/README.md](swift/README.md) |
| Android (Kotlin) | `com.dodopayments:checkout-android` | [kotlin/README.md](kotlin/README.md) |
| React Native | [`@dodopayments/react-native-checkout`](https://www.npmjs.com/package/@dodopayments/react-native-checkout) | [react-native/README.md](react-native/README.md) |
| Flutter | [`dodopayments_checkout`](https://pub.dev/packages/dodopayments_checkout) | [flutter/README.md](flutter/README.md) |

React Native and Flutter are thin wrappers over the native iOS and Android
cores — they carry no checkout logic of their own.

## Shared contract

All four SDKs expose the same shape, so integrating one means you already
understand the others:

- One call — `start(...)` — that returns a `CheckoutResult` with a `status`
  (`succeeded` / `failed` / `cancelled` / `pending` / `expired`).
- The result is a **UI hint, not proof of payment**. None of these SDKs call
  the Dodo API or hold an API key; grant access from your backend via webhook
  or by retrieving the payment with your secret key.
- An optional `onEvent` callback for logging (`opened` / `navigation` /
  `return_received` / `closed`) — never authoritative.
- A `CheckoutError` / `CheckoutException` for misuse or platform failure only
  (`INVALID_CHECKOUT_URL`, `INVALID_RETURN_URL`, `ALREADY_IN_PROGRESS`,
  `PLATFORM_ERROR`). A user cancelling or a declined payment is always a
  **result**, never a thrown error.
- Abandoned-session recovery, for when the app is killed mid-checkout.

See each SDK's README for the exact API and platform setup.

## Demo apps

Runnable reference integrations for all four platforms live under
[`demo-apps/`](demo-apps/).
