# Changelog

## 1.0.1

- No functional changes. Re-releases 1.0.0 through the automated pub.dev
  publishing workflow; the package contents are identical.

## 1.0.0

- Initial release: `DodoCheckout.instance.start(CheckoutParams)` over the
  native iOS/Android checkout cores via Pigeon; typed `CheckoutResult`,
  `CheckoutException`, and abandoned-session recovery
  (`getAbandonedSession` / `clearAbandonedSession`).
