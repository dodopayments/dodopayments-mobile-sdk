# Changelog

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

## 1.0.0

- Initial release: `DodoCheckout.start(params)` over the native iOS/Android
  checkout cores via a Turbo Module; typed `CheckoutResult`, `CheckoutError`,
  and abandoned-session recovery (`getAbandonedSession` / `clearAbandonedSession`).
