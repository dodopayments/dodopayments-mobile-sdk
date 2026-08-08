import 'dart:ui' show Color;

import 'package:flutter/foundation.dart' show immutable;

/// Everything [DodoCheckout.start] needs.
@immutable
class CheckoutParams {
  const CheckoutParams({
    required this.checkoutUrl,
    required this.returnUrl,
    this.customization,
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

  /// Appearance customization for the checkout browser.
  final BrowserCustomization? customization;

  /// Lifecycle callback for logging/analytics only — never decide outcome
  /// from events.
  final void Function(CheckoutEvent event)? onEvent;
}

/// Customizes the checkout browser's chrome. [android]/[ios] carry options
/// that only exist on that one platform — each is a no-op on the other, and
/// native only ever reads its own bag.
///
/// No shared/top-level fields: `toolbarColor` was originally shared, but
/// iOS's equivalent (`preferredBarTintColor`) is deprecated as of iOS 26
/// with no replacement and confirmed to have no visible effect there — not
/// worth a color knob that's already inert on the majority of iOS devices.
/// Same reasoning killed iOS's `controlTintColor` outright (it rested on
/// the identically-deprecated `preferredControlTintColor`).
@immutable
class BrowserCustomization {
  const BrowserCustomization({
    this.android,
    this.ios,
  });

  final AndroidBrowserOptions? android;
  final IosBrowserOptions? ios;
}

enum CloseButtonStyle {
  /// The system "X" icon.
  standard,

  /// A back-arrow icon the SDK draws itself. Android ships no built-in
  /// alternative to the "X".
  back,
}

enum CloseButtonPosition { start, end }

/// Mirrors `SFSafariViewController.DismissButtonStyle`. No Android
/// equivalent — see [CloseButtonStyle] for Android's close button.
enum DismissButtonStyle { done, close, cancel }

/// How the checkout sheet is presented on iOS. No Android equivalent —
/// Custom Tabs has no comparable page-sheet-vs-full-screen distinction in
/// this SDK.
enum PresentationStyle { pageSheet, fullScreen }

/// Forces the browser's light/dark appearance regardless of the system
/// setting. Named `BrowserColorScheme`, not `ColorScheme`, to avoid
/// colliding with `package:flutter/material.dart`'s `ColorScheme`. Exists
/// independently on [AndroidBrowserOptions] and [IosBrowserOptions] — not a
/// shared field — even though the values are identical, matching every
/// other option here.
enum BrowserColorScheme { system, light, dark }

/// Android-only chrome options — a no-op on iOS. Every field is `null` by
/// default, and `null` isn't resolved to a fallback anywhere in Dart — it
/// crosses the channel as-is, and the native side decides what "unset"
/// means (usually: don't call the corresponding `CustomTabsIntent.Builder`
/// setter at all, so the Custom Tab host's own live default applies). This
/// is deliberate: hardcoding a guess at "the platform default" in Dart can
/// go stale the moment the host changes it, silently changing behavior for
/// every integrator who never touched that field.
@immutable
class AndroidBrowserOptions {
  const AndroidBrowserOptions({
    this.toolbarColor,
    this.closeButtonStyle,
    this.closeButtonPosition,
    this.shareButtonEnabled,
    this.showTitleEnabled,
    this.urlBarHidingEnabled,
    this.bookmarksButtonEnabled,
    this.downloadsButtonEnabled,
    this.secondaryToolbarColor,
    this.navigationBarColor,
    this.navigationBarDividerColor,
    this.colorScheme,
  });

  final Color? toolbarColor;
  final CloseButtonStyle? closeButtonStyle;
  final CloseButtonPosition? closeButtonPosition;

  /// Hides the toolbar's share icon when `false`.
  final bool? shareButtonEnabled;
  final bool? showTitleEnabled;
  final bool? urlBarHidingEnabled;
  final bool? bookmarksButtonEnabled;
  final bool? downloadsButtonEnabled;
  final Color? secondaryToolbarColor;
  final Color? navigationBarColor;
  final Color? navigationBarDividerColor;

  /// Forces the Custom Tab's light/dark appearance regardless of the system
  /// setting.
  final BrowserColorScheme? colorScheme;
}

/// iOS-only chrome options — a no-op on Android. Every field is `null` by
/// default, and `null` isn't resolved to a fallback anywhere in Dart — it
/// crosses the channel as-is, and the native side decides what "unset"
/// means (usually: don't touch the corresponding `SFSafariViewController`
/// property at all, so the OS's own live default applies). This is
/// deliberate: hardcoding a guess at "the platform default" in Dart can go
/// stale the moment Apple changes it, silently changing behavior for every
/// integrator who never touched that field.
@immutable
class IosBrowserOptions {
  const IosBrowserOptions({
    this.dismissButtonStyle,
    this.barCollapsingEnabled,
    this.presentationStyle,
    this.colorScheme,
  });

  final DismissButtonStyle? dismissButtonStyle;

  /// Only has a visible effect when [presentationStyle] is
  /// [PresentationStyle.fullScreen] — confirmed by hands-on testing that
  /// `pageSheet` keeps the bars pinned regardless of this setting.
  final bool? barCollapsingEnabled;

  /// `null` resolves to [PresentationStyle.pageSheet] on the native side —
  /// unlike the other fields, that isn't a platform default being inferred:
  /// `.pageSheet` was already this SDK's own hardcoded presentation choice
  /// before this feature existed.
  final PresentationStyle? presentationStyle;

  /// Forces the sheet's light/dark appearance regardless of the system
  /// setting.
  final BrowserColorScheme? colorScheme;
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

  /// The user dismissed the checkout before any return URL arrived.
  ///
  /// **This is not a decline — do not show a failure screen for it.** The SDK
  /// only ever learns the outcome from the return URL, so a dismissal leaves
  /// the payment's real state unknown. The user may well have paid: closing
  /// the browser while the hosted "Payment Successful" page counts down its
  /// redirect produces exactly this status.
  ///
  /// Call [DodoCheckout.getAbandonedSession] for the `cks_…` session id,
  /// reconcile it server-side, and show the outcome that comes back.
  cancelled,

  /// The payment will settle later — bank transfers and other async methods.
  /// The webhook delivers the final outcome.
  ///
  /// This is also the fallback for a missing or unrecognized `status`, so an
  /// unparseable return URL lands here too — possibly with no `paymentId` or
  /// `subscriptionId` either. Treat it like [cancelled]: the outcome is not
  /// settled, so call [DodoCheckout.getAbandonedSession] and reconcile the
  /// session server-side rather than showing a terminal screen.
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
