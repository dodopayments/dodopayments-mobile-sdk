/// Dodo Payments mobile checkout for Flutter.
///
/// Open Dodo's hosted checkout in a system browser tab
/// (`SFSafariViewController` on iOS, a Custom Tab on Android) and get a clean
/// result from one call. See README.md.
library;

export 'src/checkout_exception.dart' show CheckoutErrorCode, CheckoutException;
export 'src/checkout_types.dart'
    show
        AbandonedSession,
        CheckoutEvent,
        CheckoutEventType,
        CheckoutParams,
        CheckoutResult,
        CheckoutStatus;
export 'src/dodo_checkout.dart' show DodoCheckout;
