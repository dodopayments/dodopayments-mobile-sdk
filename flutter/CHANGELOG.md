# Changelog

## 1.0.4

- Re-vendor the Swift core at v1.0.2: swiping down to dismiss the checkout
  sheet left `start`'s continuation unresumed, since
  `safariViewControllerDidFinish` only fires for the "Done" button tap, not
  the interactive swipe-to-dismiss on a `.pageSheet`. The `closed` event now
  fires for both.

## 1.0.3

- Bump `com.dodopayments.api:checkout-android` to 1.0.2 and re-vendor the
  Swift core at v1.0.1: a `CheckoutStatus.cancelled`/`.pending` result no
  longer wipes the abandoned-session record, so `getAbandonedSession()` can
  be reconciled after a dismissed or unparseable checkout instead of
  returning `null`.
- README's `reconcileAbandonedSession()` example now reconciles `pending`
  alongside `cancelled`, and only clears the record once the backend
  reports a terminal outcome.

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
