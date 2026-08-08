# Changelog

## 1.2.0

- Add `customization` on `start()`: an optional `BrowserCustomization` with
  separate `android`/`ios` bags. Android covers toolbar/secondary-toolbar/
  navigation-bar(-divider) colors, close button style and position,
  share/title/URL-bar-hiding/bookmarks/downloads toggles, and a forced
  light/dark `colorScheme`. iOS covers dismiss button style, bar-collapsing,
  presentation style, and `colorScheme`. Every field is optional and simply
  omitted when unset, so the underlying platform's own current behavior
  applies rather than this SDK asserting a value on its behalf.
- Bumps `com.dodopayments.api:checkout-android` to 1.1.0 and re-vendors the
  Swift core with the same customization support.

## 1.1.2

- Fix: swiping down to dismiss the checkout sheet left `start()`'s promise
  unresolved, which also kept the in-progress guard set — every later
  `start()` call rejected with `ALREADY_IN_PROGRESS` until app restart.
  Re-vendors the Swift core at v1.0.2, which resolves the promise with
  `cancelled` on swipe-dismiss too, same as the Done button.

## 1.1.1

- Bump `com.dodopayments.api:checkout-android` to 1.0.2 and re-vendor the
  Swift core at v1.0.1: a `CANCELLED`/`PENDING` result no longer wipes the
  abandoned-session record, so `getAbandonedSession()` can be reconciled
  after a dismissed or unparseable checkout instead of returning `null`.
- README's `reconcileAbandonedSession()` example now reconciles `pending`
  alongside `cancelled`, and only clears the record once the backend
  reports a terminal outcome.

## 1.1.0

- Expo config plugin registers the merchant callback scheme at prebuild when
  given `{ "scheme": "myappcheckout" }`: writes Android
  `manifestPlaceholders["dodoCallbackScheme"]` and an iOS `CFBundleURLTypes`
  entry. Bare plugin entry without `scheme` remains a no-op.
  - Rejects `http`/`https` and other system schemes; throws on invalid present
    schemes (no silent no-op).
  - Gradle editing is scoped to the `defaultConfig` block and skips comments
    and string literals, so a `manifestPlaceholders` map belonging to a
    `buildTypes`/`productFlavors` variant — or one that is commented out — is
    never edited by mistake. Merges into an existing `defaultConfig` map
    (including the empty `[:]` literal) and otherwise appends at the end of the
    block, so a later whole-map assignment cannot wipe the key. Re-running
    prebuild is idempotent and preserves surrounding indentation.
  - Warns when the scheme collides with `expo.scheme` (prefer a dedicated
    checkout scheme). Loads config-plugins via `expo/config-plugins` with a
    `@expo/config-plugins` fallback. iOS still needs `handleOpenURL`.

## 1.0.1

- Fix `NativeDodoCheckout.ts` codegen spec failing to parse on RN <0.80:
  import `UnsafeObject`/`EventEmitter` directly from
  `react-native/Libraries/Types/CodegenTypes` instead of the namespaced
  `CodegenTypes.X` form, which older codegen can't resolve.
- `android/build.gradle` now depends on `checkout-android:1.0.1` (was
  still pinned to `1.0.0`) and `kotlinx-coroutines-android:1.9.0` (was
  `1.11.0`) — the stale pins here previously overrode the Kotlin core's
  own fixed versions for any consuming RN app. Lowered `compileSdk`/
  `targetSdk` fallback from 36 to 35 to match.
- Correct the documented minimum React Native version from 0.76.0 to
  0.77.0 — 0.76's `CodegenTypes` module has no `EventEmitter` export, so
  this spec never actually typechecked on 0.76 in the first place.
- Document the real Android floor: `compileSdk` 34, taken from the AAR
  metadata of the AndroidX artifacts the native core depends on. There is
  no Kotlin Gradle plugin or AGP floor beyond what React Native itself
  imposes.

## 1.0.0

- Initial release: `DodoCheckout.start(params)` over the native iOS/Android
  checkout cores via a Turbo Module; typed `CheckoutResult`, `CheckoutError`,
  and abandoned-session recovery (`getAbandonedSession` / `clearAbandonedSession`).
