# Changelog

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
