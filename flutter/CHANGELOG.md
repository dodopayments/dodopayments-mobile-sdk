# Changelog

## Unreleased

- Docs: `CheckoutStatus.cancelled` means the outcome is *unknown*, not declined
  -- the user may have paid and dismissed the browser before the return URL
  fired. README now carries a worked `reconcileAbandonedSession()` example
  instead of an example `switch` that treated `cancelled` as a dead end.
- The behavioral half of this fix arrives when the native cores are bumped:
  `com.dodopayments.api:checkout-android` 1.0.2 and the Swift core's v1.0.1
  (re-vendored from the `swift/` submodule at release time).

## 1.0.2

- Bump `com.dodopayments.api:checkout-android` to `1.0.1` and downgrade
  `kotlinx-coroutines-android` to `1.9.0` to match — the old pins forced
  compileSdk 36+/AGP 8.9.1+ and a Kotlin 2.2.x-compatible compiler on
  consumers. Lowered this module's own `compileSdk` to 35 to match.
- The plugin's `compileSdk` can now be raised without forking it: set
  `dodoCompileSdk` in your app's `gradle.properties` (or root project
  `ext`). Defaults to 35. Useful if another plugin pulls in an AndroidX
  build that requires compileSdk 36.

## 1.0.1

- No functional changes. Re-releases 1.0.0 through the automated pub.dev
  publishing workflow; the package contents are identical.

## 1.0.0

- Initial release: `DodoCheckout.instance.start(CheckoutParams)` over the
  native iOS/Android checkout cores via Pigeon; typed `CheckoutResult`,
  `CheckoutException`, and abandoned-session recovery
  (`getAbandonedSession` / `clearAbandonedSession`).
