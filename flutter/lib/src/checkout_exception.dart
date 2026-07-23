import 'package:flutter/foundation.dart' show immutable;

/// Error codes thrown by [DodoCheckout.start].
///
/// These map 1:1 to the native error codes shared by the iOS and Android
/// cores. A user cancelling or a payment failing is a **result**
/// ([CheckoutStatus.cancelled] / [CheckoutStatus.failed]), never an exception.
enum CheckoutErrorCode {
  /// `checkoutUrl` is not a `checkout.dodopayments.com` /
  /// `test.checkout.dodopayments.com` session URL.
  /// Native code: `INVALID_CHECKOUT_URL`.
  invalidCheckoutUrl,

  /// `returnUrl` is not a valid absolute URL.
  /// Native code: `INVALID_RETURN_URL`.
  invalidReturnUrl,

  /// A checkout is already running. Only one can run at a time.
  /// Native code: `ALREADY_IN_PROGRESS`.
  alreadyInProgress,

  /// An unexpected platform error occurred.
  /// Native code: `PLATFORM_ERROR`.
  platformError,
}

/// Thrown by [DodoCheckout.start] for misuse or platform failure — never for
/// a user cancel or a declined payment (those are results).
@immutable
class CheckoutException implements Exception {
  const CheckoutException(this.code, this.message);

  final CheckoutErrorCode code;
  final String message;

  /// The native wire code, e.g. `INVALID_CHECKOUT_URL`.
  String get nativeCode => nativeCodeFor(code);

  @override
  String toString() => 'CheckoutException($nativeCode: $message)';

  /// Maps a native error-code string to the typed enum. Unknown codes
  /// (including Pigeon channel errors) collapse to
  /// [CheckoutErrorCode.platformError].
  static CheckoutErrorCode codeFromNative(String nativeCode) {
    switch (nativeCode) {
      case 'INVALID_CHECKOUT_URL':
        return CheckoutErrorCode.invalidCheckoutUrl;
      case 'INVALID_RETURN_URL':
        return CheckoutErrorCode.invalidReturnUrl;
      case 'ALREADY_IN_PROGRESS':
        return CheckoutErrorCode.alreadyInProgress;
      case 'PLATFORM_ERROR':
      default:
        return CheckoutErrorCode.platformError;
    }
  }

  /// The wire string for a typed code.
  static String nativeCodeFor(CheckoutErrorCode code) {
    switch (code) {
      case CheckoutErrorCode.invalidCheckoutUrl:
        return 'INVALID_CHECKOUT_URL';
      case CheckoutErrorCode.invalidReturnUrl:
        return 'INVALID_RETURN_URL';
      case CheckoutErrorCode.alreadyInProgress:
        return 'ALREADY_IN_PROGRESS';
      case CheckoutErrorCode.platformError:
        return 'PLATFORM_ERROR';
    }
  }
}
