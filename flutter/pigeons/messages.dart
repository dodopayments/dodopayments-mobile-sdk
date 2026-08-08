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
    this.customization,
  });

  /// Absolute session URL from the merchant backend.
  String checkoutUrl;

  /// Absolute URL the native SDK watches for. Its scheme must be registered
  /// as a URL type in Info.plist (iOS) / an intent-filter on the redirect
  /// activity (Android), so the OS routes the checkout return back to this
  /// app.
  String returnUrl;

  /// Appearance customization for the checkout browser.
  NativeBrowserCustomization? customization;
}

/// `standard` (not `default` — a reserved Dart keyword) is the system "X"
/// icon on Android; `back` is a back-arrow icon the SDK draws itself. No
/// iOS equivalent — see [NativeDismissButtonStyle] for iOS's dismiss button.
enum NativeCloseButtonStyle {
  standard,
  back,
}

enum NativeCloseButtonPosition {
  start,
  end,
}

/// Mirrors `SFSafariViewController.DismissButtonStyle`. No Android
/// equivalent — see [NativeCloseButtonStyle] for Android's close button.
enum NativeDismissButtonStyle {
  done,
  close,
  cancel,
}

/// Mirrors `UIViewController.modalPresentationStyle` as used to present the
/// checkout sheet. No Android equivalent — Custom Tabs has no comparable
/// page-sheet-vs-full-screen distinction in this SDK.
enum NativePresentationStyle {
  pageSheet,
  fullScreen,
}

/// Forces the browser's light/dark appearance regardless of the system
/// setting. Exists independently on `android` and `ios` (not shared) even
/// though the values are identical, matching every other field here.
enum NativeBrowserColorScheme {
  system,
  light,
  dark,
}

/// Mirrors the native `BrowserCustomization`. `android`/`ios` carry options
/// that only exist on that one platform — native only ever reads its own
/// bag, so this crosses both platforms from one Dart type without either
/// side seeing the other's fields.
///
/// No shared/top-level fields: `toolbarColor` was originally shared, but
/// iOS's equivalent (`preferredBarTintColor`) is deprecated as of iOS 26
/// with no replacement and confirmed to have no visible effect there — not
/// worth a color knob that's already inert on the majority of iOS devices.
/// Same reasoning killed iOS's `controlTintColor` outright (it rested on
/// the identically-deprecated `preferredControlTintColor`).
class NativeBrowserCustomization {
  NativeBrowserCustomization({
    this.android,
    this.ios,
  });

  NativeAndroidBrowserOptions? android;
  NativeIosBrowserOptions? ios;
}

/// Every field is `null` by default. `null` isn't resolved to a fallback
/// anywhere in Dart — it crosses the channel as-is, and the native side
/// decides what "unset" means (usually: don't call the corresponding
/// `CustomTabsIntent.Builder` setter at all, so the Custom Tab host's own
/// live default applies). This is deliberate: hardcoding a guess at "the
/// platform default" in Dart can go stale the moment the host changes it,
/// silently changing behavior for every integrator who never touched that
/// field.
///
/// Pigeon encodes this class as a fixed-position list, not a keyed map —
/// `decode()` reads `result[0]`, `result[1]`, ... by index, matching
/// declaration order exactly. Future fields must be appended at the end,
/// never inserted, or every field after the insertion point silently reads
/// the wrong value. Regenerate all three codegen targets together after any
/// change (`dart run pigeon --input pigeons/messages.dart`).
class NativeAndroidBrowserOptions {
  NativeAndroidBrowserOptions({
    this.toolbarColor,
    this.closeButtonStyle,
    this.closeButtonPosition,
    this.shareButtonEnabled,
    this.showTitleEnabled,
    this.urlBarHidingEnabled,
    this.bookmarksButtonEnabled,
    this.downloadsButtonEnabled,
    this.navigationBarColor,
    this.navigationBarDividerColor,
    this.colorScheme,
  });

  /// ARGB, i.e. `Color.toARGB32()`.
  int? toolbarColor;
  NativeCloseButtonStyle? closeButtonStyle;
  NativeCloseButtonPosition? closeButtonPosition;

  /// Hides the toolbar's share icon when `false`.
  bool? shareButtonEnabled;
  bool? showTitleEnabled;
  bool? urlBarHidingEnabled;
  bool? bookmarksButtonEnabled;
  bool? downloadsButtonEnabled;
  int? navigationBarColor;
  int? navigationBarDividerColor;
  NativeBrowserColorScheme? colorScheme;
}

/// Every field is `null` by default. `null` isn't resolved to a fallback
/// anywhere in Dart — it crosses the channel as-is, and the native side
/// decides what "unset" means (usually: don't touch the corresponding
/// `SFSafariViewController` property at all, so the OS's own live default
/// applies). This is deliberate: hardcoding a guess at "the platform
/// default" in Dart can go stale the moment Apple changes it, silently
/// changing behavior for every integrator who never touched that field.
///
/// Same fixed-position wire encoding as [NativeAndroidBrowserOptions] —
/// append future fields at the end, never insert.
class NativeIosBrowserOptions {
  NativeIosBrowserOptions({
    this.dismissButtonStyle,
    this.barCollapsingEnabled,
    this.presentationStyle,
    this.colorScheme,
  });

  NativeDismissButtonStyle? dismissButtonStyle;

  /// Only has a visible effect when [presentationStyle] is `fullScreen` —
  /// confirmed by hands-on testing that `pageSheet` keeps the bars pinned
  /// regardless of this setting.
  bool? barCollapsingEnabled;
  NativePresentationStyle? presentationStyle;
  NativeBrowserColorScheme? colorScheme;
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
  returnReceived,
  closed,
}

class NativeCheckoutEvent {
  NativeCheckoutEvent({required this.type});

  NativeEventType type;
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
