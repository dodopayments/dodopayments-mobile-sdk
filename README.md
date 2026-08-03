# Dodo Payments Mobile SDK

<p align="left">
  <a href="https://discord.gg/bYqAp4ayYh">
    <img src="https://img.shields.io/discord/1305511580854779984?label=Join%20Discord&logo=discord" alt="Join Discord" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License: Apache 2.0" />
  </a>
</p>

Native checkout SDKs for Dodo Payments' hosted checkout, for iOS, Android,
React Native, and Flutter.

Each SDK opens the checkout in a system browser surface. `SFSafariViewController`
on iOS, a Custom Tab (`androidx.browser.customtabs`) on Android.

## Install

**Swift (iOS)**

```swift
// Package.swift
.package(url: "https://github.com/dodopayments/dodopayments-mobile-sdk-ios", from: "1.0.0")
```

**Kotlin (Android)**

```kotlin
// build.gradle.kts
implementation("com.dodopayments.api:checkout-android:1.0.1")
```

**React Native**

```sh
npm i @dodopayments/react-native-checkout
```

**Flutter**

```yaml
# pubspec.yaml
dependencies:
  dodopayments_checkout: ^1.0.0
```

Check each SDK's README (linked below) for setup and usage.

## SDKs

| Platform | Package | Docs |
|---|---|---|
| iOS (Swift) | [Swift Package Manager](https://github.com/dodopayments/dodopayments-mobile-sdk-ios) | [dodopayments-mobile-sdk-ios/README.md](https://github.com/dodopayments/dodopayments-mobile-sdk-ios/blob/main/README.md) |
| Android (Kotlin) | `com.dodopayments.api:checkout-android` | [kotlin/README.md](kotlin/README.md) |
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
- An optional `onEvent` callback for logging (`opened` / `return_received` /
  `closed`) — never authoritative.
- A `CheckoutError` / `CheckoutException` for misuse or platform failure only
  (`INVALID_CHECKOUT_URL`, `INVALID_RETURN_URL`, `ALREADY_IN_PROGRESS`,
  `PLATFORM_ERROR`). A user cancelling or a declined payment is always a
  **result**, never a thrown error.
- Abandoned-session recovery, for every checkout that ends without the SDK
  seeing its return URL — the app killed mid-checkout, or a `cancelled` result.

See each SDK's README for the exact API and platform setup.

### `cancelled` does not mean the payment failed

The SDK only ever learns the outcome from the return URL. A `cancelled` status
means the user dismissed the browser before that URL arrived, so the real state
is **unknown** — a user who pays and then closes the sheet while the hosted
"Payment Successful" page counts down its redirect lands here, exactly like a
user who closed it without paying.

Don't map `cancelled` to a failure screen. The SDK keeps the checkout session on
record for precisely this case: read it with `getAbandonedSession()`, resolve
`sessionId` against your backend, and show what comes back.

## Project structure

```
dodopayments-mobile-sdk/
├── swift/          # iOS core (Swift Package). Git submodule → dodopayments-mobile-sdk-ios.
├── kotlin/         # Android core (Kotlin/Gradle). Published to Maven Central.
├── react-native/   # Turbo Module wrapper over the iOS/Android cores. Published to npm.
└── flutter/        # Pigeon wrapper over the iOS/Android cores. Published to pub.dev.
```

## License

Apache License 2.0, see [LICENSE](LICENSE) for details.

