# Changelog

## 1.0.2

- Bump `com.dodopayments.api:checkout-android` to `1.0.1` and downgrade
  `kotlinx-coroutines-android` to `1.9.0` to match — the old pins forced
  compileSdk 36+/AGP 8.9.1+ and a Kotlin 2.2.x-compatible compiler on
  consumers. Lowered this module's own `compileSdk` to 35 to match.

## 1.0.1

- No functional changes. Re-releases 1.0.0 through the automated pub.dev
  publishing workflow; the package contents are identical.

## 1.0.0

- Initial release: `DodoCheckout.instance.start(CheckoutParams)` over the
  native iOS/Android checkout cores via Pigeon; typed `CheckoutResult`,
  `CheckoutException`, and abandoned-session recovery
  (`getAbandonedSession` / `clearAbandonedSession`).
