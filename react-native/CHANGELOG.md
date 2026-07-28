# Changelog

## 1.0.1

- Fix `NativeDodoCheckout.ts` codegen spec failing to parse on RN <0.80:
  import `UnsafeObject`/`EventEmitter` directly from
  `react-native/Libraries/Types/CodegenTypes` instead of the namespaced
  `CodegenTypes.X` form, which older codegen can't resolve.

## 1.0.0

- Initial release: `DodoCheckout.start(params)` over the native iOS/Android
  checkout cores via a Turbo Module; typed `CheckoutResult`, `CheckoutError`,
  and abandoned-session recovery (`getAbandonedSession` / `clearAbandonedSession`).
