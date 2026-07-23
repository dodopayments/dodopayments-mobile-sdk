// Pigeon interface definition for dodopayments_checkout.
//
// This file is the single source of truth for the typed platform channel
// between Dart and the two native cores. After editing it, regenerate the
// bindings with:
//
//   dart run pigeon --input pigeons/messages.dart
//
// which rewrites:
//   lib/src/messages.g.dart
//   android/src/main/kotlin/com/dodopayments/checkout_flutter/Messages.g.kt
//   ios/Classes/Messages.g.swift
//
// The DTOs here are wire types only. The public Dart API (CheckoutParams,
// CheckoutResult, CheckoutException, ...) lives in lib/src and is mapped
// to/from these types by the DodoCheckout facade.

import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/src/messages.g.dart',
    kotlinOut:
        'android/src/main/kotlin/com/dodopayments/checkout_flutter/Messages.g.kt',
    kotlinOptions: KotlinOptions(package: 'com.dodopayments.checkout_flutter'),
    swiftOut: 'ios/Classes/Messages.g.swift',
    dartPackageName: 'dodopayments_checkout',
  ),
)

/// Mirrors `CheckoutStatus` in the native cores.
enum NativeCheckoutStatus {
  succeeded,
  failed,
  cancelled,
  pending,
  expired,
}

class StartRequest {
  StartRequest({
    required this.checkoutUrl,
    required this.returnUrl,
  });

  /// Absolute session URL from the merchant backend.
  String checkoutUrl;

  /// Absolute URL the native SDK watches for. Its scheme must be registered
  /// as a URL type in Info.plist (iOS) / an intent-filter on the redirect
  /// activity (Android), so the OS routes the checkout return back to this
  /// app.
  String returnUrl;
}

/// Mirrors the native `CheckoutResult`.
class NativeCheckoutResult {
  NativeCheckoutResult({
    required this.status,
    this.paymentId,
    this.subscriptionId,
    this.licenseKeys,
    this.customerEmail,
    required this.raw,
  });

  NativeCheckoutStatus status;
  String? paymentId;
  String? subscriptionId;
  List<String>? licenseKeys;
  String? customerEmail;

  /// All query parameters from the return_url, verbatim.
  Map<String, String> raw;
}

/// Mirrors the native `AbandonedSession`.
class NativeAbandonedSession {
  NativeAbandonedSession({
    required this.sessionId,
    required this.createdAtMillis,
  });

  String sessionId;

  /// Milliseconds since the Unix epoch (UTC).
  int createdAtMillis;
}

/// Mirrors `CheckoutEvent` in the native cores. Lifecycle-only — never used
/// to decide the checkout outcome.
enum NativeEventType {
  opened,
  navigation,
  returnReceived,
  closed,
}

class NativeCheckoutEvent {
  NativeCheckoutEvent({required this.type, this.host});

  NativeEventType type;

  /// Only set when [type] is `navigation`.
  String? host;
}

/// Implemented natively (Swift/Kotlin), called from Dart. Presents an
/// `SFSafariViewController` sheet on iOS, a Custom Tab on Android.
///
/// Errors are surfaced as PlatformException with `code` set to one of the
/// shared native error codes: INVALID_CHECKOUT_URL, INVALID_RETURN_URL,
/// ALREADY_IN_PROGRESS, PLATFORM_ERROR.
@HostApi()
abstract class DodoCheckoutHostApi {
  /// Presents the checkout and completes when it resolves. Lifecycle events
  /// for this call are delivered via [DodoCheckoutFlutterApi.onCheckoutEvent]
  /// for the call's duration.
  @async
  NativeCheckoutResult start(StartRequest request);

  NativeAbandonedSession? getAbandonedSession();

  void clearAbandonedSession();

  /// iOS only — forward incoming URLs here from your app's own URL-handling.
  /// Required: `SFSafariViewController` has no in-process way to catch its
  /// own return URL. No-op returning `false` on Android (Custom Tabs there is
  /// fully self-contained via Activities). Returns `true` if the URL belonged
  /// to an in-flight checkout.
  bool handleOpenURL(String url);
}

/// Implemented in Dart, called from native. Delivers lifecycle events for
/// whichever checkout is currently in flight — logging/analytics only.
@FlutterApi()
abstract class DodoCheckoutFlutterApi {
  void onCheckoutEvent(NativeCheckoutEvent event);
}
