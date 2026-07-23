import 'package:flutter/foundation.dart' show immutable;

/// Everything [DodoCheckout.start] needs.
@immutable
class CheckoutParams {
  const CheckoutParams({
    required this.checkoutUrl,
    required this.returnUrl,
    this.onEvent,
  });

  /// The session URL from your backend. Must be a
  /// `checkout.dodopayments.com` / `test.checkout.dodopayments.com`
  /// `/session/…` URL. Test vs live is read from the host — no mode flag.
  final Uri checkoutUrl;

  /// The URL the SDK watches for. Any absolute URL the checkout ends up
  /// navigating to (the session's `return_url`); matched on
  /// scheme+host+path. It never has to resolve. One shared URL works for both
  /// platforms — no OS deep-link wiring exists. Its scheme must be
  /// registered as a URL type in Info.plist (iOS) / an intent-filter on the
  /// redirect activity (Android), so the OS routes the checkout return back
  /// to this app. iOS only: you must also forward incoming URLs into
  /// [DodoCheckout.handleOpenURL] — `SFSafariViewController` has no
  /// in-process way to catch its own return URL.
  final Uri returnUrl;

  /// Lifecycle callback for logging/analytics only — never decide outcome
  /// from events.
  final void Function(CheckoutEvent event)? onEvent;
}

/// Lifecycle events emitted during a checkout, for logging/analytics only.
///
/// **Never decide the outcome from an event.** Use the [CheckoutResult]
/// returned by [DodoCheckout.start].
enum CheckoutEventType {
  /// The checkout screen was presented.
  opened,

  /// A navigation matching `returnUrl` was intercepted.
  returnReceived,

  /// The checkout screen was dismissed.
  closed,
}

@immutable
class CheckoutEvent {
  const CheckoutEvent({required this.type});

  final CheckoutEventType type;
}

/// The outcome of a checkout, derived entirely from the query string on the
/// merchant's `return_url`.
///
/// This is a **UI signal only**. It is not proof of payment: the SDK never
/// calls the Dodo API and holds no API key. Grant access on your backend from
/// the webhook (`payment.succeeded` / `subscription.active`).
enum CheckoutStatus {
  /// One-time payment settled (`status=succeeded`) or subscription became
  /// active (`status=active`).
  succeeded,

  /// The payment was declined (`status=failed`).
  failed,

  /// The user closed the checkout before the return fired.
  cancelled,

  /// The payment will settle later — bank transfers and other async methods.
  /// The webhook delivers the final outcome.
  pending,

  /// The checkout session expired before completion.
  expired,
}

/// The result returned from [DodoCheckout.start].
///
/// Everything here comes from the `return_url` query parameters. [raw] holds
/// the complete, unmodified parameter set so callers can read fields the typed
/// surface does not model.
@immutable
class CheckoutResult {
  const CheckoutResult({
    required this.status,
    this.paymentId,
    this.subscriptionId,
    this.licenseKeys,
    this.customerEmail,
    this.raw = const <String, String>{},
  });

  final CheckoutStatus status;
  final String? paymentId;
  final String? subscriptionId;
  final List<String>? licenseKeys;
  final String? customerEmail;

  /// All query parameters from the `return_url`, verbatim.
  final Map<String, String> raw;

  @override
  String toString() =>
      'CheckoutResult(status: $status, paymentId: $paymentId, '
      'subscriptionId: $subscriptionId, licenseKeys: $licenseKeys, '
      'customerEmail: $customerEmail, raw: $raw)';
}

/// A checkout the app was killed or dismissed in the middle of.
///
/// The SDK cannot know the payment's real outcome after the process dies — the
/// merchant reconciles it server-side (webhook or `payments.retrieve`). This
/// record only tells the app *that* a checkout was interrupted.
@immutable
class AbandonedSession {
  const AbandonedSession({
    required this.sessionId,
    required this.createdAt,
  });

  /// The `cks_…` checkout session id.
  final String sessionId;

  /// When the interrupted checkout was started.
  final DateTime createdAt;

  @override
  String toString() =>
      'AbandonedSession(sessionId: $sessionId, createdAt: $createdAt)';
}
