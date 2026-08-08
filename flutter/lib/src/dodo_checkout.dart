import 'dart:async';

import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:flutter/services.dart' show PlatformException;

import 'checkout_exception.dart';
import 'checkout_types.dart';
import 'messages.g.dart';

/// Entry point for the Dodo Payments mobile checkout (Flutter).
///
/// This is a **thin wrapper**: all checkout logic lives in the native iOS
/// (`DodoCheckout`, Swift) and Android (`com.dodopayments:checkout-android`)
/// cores. The wrapper passes the call through over a typed Pigeon channel and
/// maps the result back.
///
/// ```dart
/// final result = await DodoCheckout.instance.start(
///   CheckoutParams(
///     checkoutUrl: Uri.parse(checkoutUrlFromYourBackend),
///     returnUrl: Uri.parse('myapp://checkout/return'),
///   ),
/// );
/// ```
///
/// The SDK contains **zero networking code** and holds **no API key**. The
/// result is a UI hint — grant access server-side from the webhook.
class DodoCheckout {
  DodoCheckout._(this._api) {
    DodoCheckoutFlutterApi.setUp(_FlutterApiImpl(_eventController));
  }

  /// Creates a facade over a custom host API — for tests only.
  @visibleForTesting
  DodoCheckout.forTesting(DodoCheckoutHostApi api) : _api = api;

  /// Simulates a native-pushed lifecycle event — for tests only.
  @visibleForTesting
  void debugEmitEvent(NativeCheckoutEvent event) => _eventController.add(event);

  /// The shared instance backed by the real platform channel.
  static final DodoCheckout instance = DodoCheckout._(DodoCheckoutHostApi());

  final DodoCheckoutHostApi _api;
  final StreamController<NativeCheckoutEvent> _eventController =
      StreamController<NativeCheckoutEvent>.broadcast();

  /// Presents Dodo's hosted checkout in a system browser tab
  /// (`SFSafariViewController` on iOS, a Custom Tab on Android) and completes
  /// with the outcome once the checkout navigates to `returnUrl` (or the user
  /// closes the tab).
  ///
  /// Throws a [CheckoutException] for invalid input, a concurrent checkout
  /// ([CheckoutErrorCode.alreadyInProgress] — only one checkout can run at a
  /// time), or a platform failure. A cancel or a declined payment is a
  /// *result*, not an exception.
  Future<CheckoutResult> start(CheckoutParams params) async {
    final void Function(CheckoutEvent event)? onEvent = params.onEvent;
    final StreamSubscription<NativeCheckoutEvent>? subscription = onEvent ==
            null
        ? null
        : _eventController.stream.listen((NativeCheckoutEvent event) {
            onEvent(CheckoutEvent(
              type: switch (event.type) {
                NativeEventType.opened => CheckoutEventType.opened,
                NativeEventType.returnReceived =>
                  CheckoutEventType.returnReceived,
                NativeEventType.closed => CheckoutEventType.closed,
              },
            ));
          });

    try {
      final NativeCheckoutResult native =
          await _api.start(_toStartRequest(params));
      return _toCheckoutResult(native);
    } on PlatformException catch (e) {
      throw CheckoutException(
        CheckoutException.codeFromNative(e.code),
        e.message ?? e.code,
      );
    } finally {
      await subscription?.cancel();
    }
  }

  /// The session of a checkout that ended without a confirmed outcome, or
  /// `null`.
  ///
  /// Set whenever the SDK never saw a return URL it could resolve to a durable
  /// outcome — the app was killed mid-flow, `start` completed with
  /// [CheckoutStatus.cancelled] because the user dismissed the browser, or it
  /// completed with [CheckoutStatus.pending], which is also the fallback for
  /// an unparseable return URL. Check this on launch *and* after every
  /// `cancelled` or `pending` result, reconcile the session server-side, then
  /// call [clearAbandonedSession] once the outcome is terminal.
  Future<AbandonedSession?> getAbandonedSession() async {
    final NativeAbandonedSession? native = await _api.getAbandonedSession();
    if (native == null) return null;
    return AbandonedSession(
      sessionId: native.sessionId,
      createdAt: DateTime.fromMillisecondsSinceEpoch(
        native.createdAtMillis,
        isUtc: true,
      ),
    );
  }

  /// Clears the abandoned-session record after it has been reconciled.
  Future<void> clearAbandonedSession() => _api.clearAbandonedSession();

  /// iOS only — forward incoming URLs here from your app's own URL-handling
  /// (e.g. an `app_links`/`uni_links` listener). Required: `SFSafariViewController`
  /// has no in-process way to catch its own return URL. A no-op returning
  /// `false` on Android. Returns `true` if the URL belonged to an in-flight
  /// checkout.
  Future<bool> handleOpenURL(String url) => _api.handleOpenURL(url);

  // -- Mapping between the public types and the Pigeon wire DTOs. -----------

  static StartRequest _toStartRequest(CheckoutParams params) {
    return StartRequest(
      checkoutUrl: params.checkoutUrl.toString(),
      returnUrl: params.returnUrl.toString(),
      customization: _toNativeBrowserCustomization(
        params.customization,
      ),
    );
  }

  static NativeBrowserCustomization? _toNativeBrowserCustomization(
    BrowserCustomization? customization,
  ) {
    if (customization == null) return null;
    final AndroidBrowserOptions? android = customization.android;
    final IosBrowserOptions? ios = customization.ios;
    return NativeBrowserCustomization(
      android: android == null
          ? null
          : NativeAndroidBrowserOptions(
              toolbarColor: android.toolbarColor?.toARGB32(),
              closeButtonStyle: switch (android.closeButtonStyle) {
                null => null,
                CloseButtonStyle.standard => NativeCloseButtonStyle.standard,
                CloseButtonStyle.back => NativeCloseButtonStyle.back,
              },
              closeButtonPosition: switch (android.closeButtonPosition) {
                null => null,
                CloseButtonPosition.start => NativeCloseButtonPosition.start,
                CloseButtonPosition.end => NativeCloseButtonPosition.end,
              },
              shareButtonEnabled: android.shareButtonEnabled,
              showTitleEnabled: android.showTitleEnabled,
              urlBarHidingEnabled: android.urlBarHidingEnabled,
              bookmarksButtonEnabled: android.bookmarksButtonEnabled,
              downloadsButtonEnabled: android.downloadsButtonEnabled,
              navigationBarColor: android.navigationBarColor?.toARGB32(),
              navigationBarDividerColor: android.navigationBarDividerColor
                  ?.toARGB32(),
              colorScheme: switch (android.colorScheme) {
                null => null,
                BrowserColorScheme.system => NativeBrowserColorScheme.system,
                BrowserColorScheme.light => NativeBrowserColorScheme.light,
                BrowserColorScheme.dark => NativeBrowserColorScheme.dark,
              },
            ),
      ios: ios == null
          ? null
          : NativeIosBrowserOptions(
              dismissButtonStyle: switch (ios.dismissButtonStyle) {
                null => null,
                DismissButtonStyle.done => NativeDismissButtonStyle.done,
                DismissButtonStyle.close => NativeDismissButtonStyle.close,
                DismissButtonStyle.cancel => NativeDismissButtonStyle.cancel,
              },
              barCollapsingEnabled: ios.barCollapsingEnabled,
              presentationStyle: switch (ios.presentationStyle) {
                null => null,
                PresentationStyle.pageSheet =>
                  NativePresentationStyle.pageSheet,
                PresentationStyle.fullScreen =>
                  NativePresentationStyle.fullScreen,
              },
              colorScheme: switch (ios.colorScheme) {
                null => null,
                BrowserColorScheme.system => NativeBrowserColorScheme.system,
                BrowserColorScheme.light => NativeBrowserColorScheme.light,
                BrowserColorScheme.dark => NativeBrowserColorScheme.dark,
              },
            ),
    );
  }

  static CheckoutResult _toCheckoutResult(NativeCheckoutResult native) {
    return CheckoutResult(
      status: switch (native.status) {
        NativeCheckoutStatus.succeeded => CheckoutStatus.succeeded,
        NativeCheckoutStatus.failed => CheckoutStatus.failed,
        NativeCheckoutStatus.cancelled => CheckoutStatus.cancelled,
        NativeCheckoutStatus.pending => CheckoutStatus.pending,
        NativeCheckoutStatus.expired => CheckoutStatus.expired,
      },
      paymentId: native.paymentId,
      subscriptionId: native.subscriptionId,
      licenseKeys: native.licenseKeys,
      customerEmail: native.customerEmail,
      raw: Map<String, String>.unmodifiable(native.raw),
    );
  }
}

/// Forwards native-pushed lifecycle events onto the broadcast stream that
/// [DodoCheckout.start] listens on for the duration of each call.
class _FlutterApiImpl implements DodoCheckoutFlutterApi {
  _FlutterApiImpl(this._controller);

  final StreamController<NativeCheckoutEvent> _controller;

  @override
  void onCheckoutEvent(NativeCheckoutEvent event) {
    _controller.add(event);
  }
}
