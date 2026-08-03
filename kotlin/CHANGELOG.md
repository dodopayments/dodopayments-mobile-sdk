# Changelog

## 1.0.2

- Fix: a `CANCELLED` result no longer wipes the abandoned-session record.
  Dismissing the Custom Tab is the one outcome the SDK cannot vouch for -- no
  return URL arrived, so the payment may well have succeeded (e.g. tapping the
  close button while the hosted success page counts down its redirect). The
  `cks_...` session id now survives, so `getAbandonedSession()` returns it and
  the merchant can reconcile server-side instead of guessing. Statuses parsed
  off the return URL still clear the record as before, and pre-presentation
  failures still clear it so no phantom session is left behind.
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
