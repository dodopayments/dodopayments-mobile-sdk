# Changelog

## 1.0.0

- Initial release: `DodoCheckout.start(activity, params, onEvent)` and a
  launcher-style `DodoCheckout.contract()` over a Custom Tab; typed
  `CheckoutResult`, `CheckoutError`, and abandoned-session recovery
  (`getAbandonedSession` / `clearAbandonedSession`).
- `BrowserRedirectActivity` auto-provisioned via the
  `manifestPlaceholders["dodoCallbackScheme"]` Gradle property, with a
  fail-fast resolvability check before presenting the Custom Tab.
