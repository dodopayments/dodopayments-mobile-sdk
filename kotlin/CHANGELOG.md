# Changelog

## 1.1.0

- Add `BrowserCustomization` on `CheckoutParams`: toolbar and
  navigation-bar(-divider) colors, close button style (`DEFAULT`/`BACK`) and
  position, share/title/URL-bar-hiding/bookmarks/downloads toggles, and a
  forced light/dark `colorScheme`. Every field defaults to `null`, and `null`
  means the corresponding `CustomTabsIntent.Builder` setter is never called
  at all — the Custom Tab host's own current behavior applies rather than
  this SDK asserting a value on its behalf.

## 1.0.2

- Fix: a `CANCELLED` or `PENDING` result no longer wipes the abandoned-session
  record. Dismissing the Custom Tab is one outcome the SDK cannot vouch for --
  no return URL arrived, so the payment may well have succeeded (e.g. tapping
  the close button while the hosted success page counts down its redirect).
  `PENDING` is the other: it's also the fallback for a missing or unrecognized
  `status`, so a malformed return URL landed here too, sometimes with no
  `paymentId`/`subscriptionId` either -- clearing then left no handle at all.
  The `cks_...` session id now survives both, so `getAbandonedSession()`
  returns it and the merchant can reconcile server-side instead of guessing.
  Statuses parsed off the return URL with a durable outcome still clear the
  record as before, and pre-presentation failures still clear it so no
  phantom session is left behind -- except a checkout activity recreated by a
  late redirect with no launch parameters, which can follow a real payment in
  a now-dead process and no longer clears blindly.
- No API change: the five statuses and their triggers are untouched.

## 1.0.1

- Downgrade `androidx.activity`/`androidx.browser` to versions that don't
  force compileSdk 36+/AGP 8.9.1+ on consumers, and `kotlinx-coroutines-android`
  to a version compatible with older Kotlin compilers. Lowered our own
  `compileSdk` to 35 to match.

## 1.0.0

- Initial release: `DodoCheckout.start(activity, params, onEvent)` and a
  launcher-style `DodoCheckout.contract()` over a Custom Tab; typed
  `CheckoutResult`, `CheckoutError`, and abandoned-session recovery
  (`getAbandonedSession` / `clearAbandonedSession`).
- `BrowserRedirectActivity` auto-provisioned via the
  `manifestPlaceholders["dodoCallbackScheme"]` Gradle property, with a
  fail-fast resolvability check before presenting the Custom Tab.
