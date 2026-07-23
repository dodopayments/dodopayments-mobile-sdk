// Example app for dodopayments_checkout.
//
// Mints a real test-mode checkout session (stands in for your backend — in
// production this call, with your secret key, happens server-side) and opens
// it in a system browser tab (SFSafariViewController on iOS, a Custom Tab on
// Android).

import 'dart:convert';

import 'package:app_links/app_links.dart';
import 'package:dodopayments_checkout/dodopayments_checkout.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

/// Demo configuration. Never ship an API key in a production app — this
/// mints client-side purely for convenience. Pass yours with:
///   flutter run --dart-define=DODO_API_KEY=...
class DemoConfig {
  static const apiBase = 'https://test.dodopayments.com';
  static const apiKey = String.fromEnvironment('DODO_API_KEY');
  static const productId = 'pdt_0NhEeXtMU6hrL5WGOk0x6'; // "Pro License"
  static const productName = 'Pro License';
  static const productPrice = '\$10.00';
  static const customerName = 'pankaj test';
  static const customerEmail = 'pankaj@example.com';

  /// Requires registering `dododemo` as a URL scheme:
  ///  - iOS: CFBundleURLTypes in ios/Runner/Info.plist.
  ///  - Android: an intent-filter on BrowserRedirectActivity in
  ///    android/app/src/main/AndroidManifest.xml (see that class's kdoc in
  ///    the com.dodopayments:checkout-android source for the exact block).
  static const callbackUrlScheme = 'dododemo';
  static const returnUrl = '$callbackUrlScheme://checkout/return';
}

Future<String> mintCheckoutSession(String returnUrl) async {
  if (DemoConfig.apiKey.isEmpty) {
    throw StateError(
      'Pass your test API key with --dart-define=DODO_API_KEY=...',
    );
  }
  final response = await http.post(
    Uri.parse('${DemoConfig.apiBase}/checkouts'),
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ${DemoConfig.apiKey}',
    },
    body: jsonEncode({
      'product_cart': [
        {'product_id': DemoConfig.productId, 'quantity': 1},
      ],
      'customer': {
        'name': DemoConfig.customerName,
        'email': DemoConfig.customerEmail,
      },
      'return_url': returnUrl,
      'feature_flags': {'redirect_immediately': true},
    }),
  );
  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw StateError('Session create failed: ${response.body}');
  }
  final json = jsonDecode(response.body) as Map<String, dynamic>;
  final checkoutUrl = json['checkout_url'] as String?;
  if (checkoutUrl == null) {
    throw StateError('No checkout_url in response.');
  }
  return checkoutUrl;
}

void main() {
  runApp(const ExampleApp());
}

class ExampleApp extends StatelessWidget {
  const ExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Dodo Checkout Example',
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF6366F1),
        useMaterial3: true,
      ),
      home: const CheckoutDemoPage(),
    );
  }
}

sealed class DemoScreen {
  const DemoScreen();
}

class HomeScreen extends DemoScreen {
  const HomeScreen();
}

class BusyScreen extends DemoScreen {
  const BusyScreen();
}

class ResultScreenState extends DemoScreen {
  const ResultScreenState(this.result);
  final CheckoutResult result;
}

class ErrorScreenState extends DemoScreen {
  const ErrorScreenState(this.message);
  final String message;
}

class CheckoutDemoPage extends StatefulWidget {
  const CheckoutDemoPage({super.key});

  @override
  State<CheckoutDemoPage> createState() => _CheckoutDemoPageState();
}

class _CheckoutDemoPageState extends State<CheckoutDemoPage> {
  DemoScreen _screen = const HomeScreen();
  late final AppLinks _appLinks;

  @override
  void initState() {
    super.initState();
    // Required: SFSafariViewController has no in-process way to catch its
    // own return URL, so the app forwards incoming URLs into the SDK
    // itself. No-op on Android.
    _appLinks = AppLinks();
    _appLinks.uriLinkStream.listen((uri) {
      DodoCheckout.instance.handleOpenURL(uri.toString());
    });
  }

  Future<void> _buy() async {
    setState(() => _screen = const BusyScreen());
    try {
      final checkoutUrl = await mintCheckoutSession(DemoConfig.returnUrl);
      final result = await DodoCheckout.instance.start(
        CheckoutParams(
          checkoutUrl: Uri.parse(checkoutUrl),
          returnUrl: Uri.parse(DemoConfig.returnUrl),
          onEvent: (event) => debugPrint('[DodoCheckout] ${event.type}'),
        ),
      );
      if (mounted) setState(() => _screen = ResultScreenState(result));
    } on CheckoutException catch (e) {
      if (mounted) {
        setState(() => _screen = ErrorScreenState('${e.nativeCode}: ${e.message}'));
      }
    } catch (e) {
      if (mounted) setState(() => _screen = ErrorScreenState(e.toString()));
    }
  }

  void _goHome() => setState(() => _screen = const HomeScreen());

  @override
  Widget build(BuildContext context) {
    return switch (_screen) {
      HomeScreen() => PriceScreen(onBuy: _buy),
      BusyScreen() => const BusyScreenView(),
      ResultScreenState(:final result) => ResultScreenView(
          result: result,
          onHome: _goHome,
        ),
      ErrorScreenState(:final message) => ErrorScreenView(
          message: message,
          onHome: _goHome,
        ),
    };
  }
}

class PriceScreen extends StatelessWidget {
  const PriceScreen({
    super.key,
    required this.onBuy,
  });

  final VoidCallback onBuy;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('⚡', style: TextStyle(fontSize: 56)),
                const SizedBox(height: 24),
                Text(
                  DemoConfig.productName,
                  style: Theme.of(context)
                      .textTheme
                      .headlineMedium
                      ?.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                Text(
                  DemoConfig.productPrice,
                  style: Theme.of(context)
                      .textTheme
                      .displaySmall
                      ?.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'One-time purchase',
                  style: Theme.of(context)
                      .textTheme
                      .bodyMedium
                      ?.copyWith(color: Colors.grey.shade600),
                ),
                const SizedBox(height: 32),
                FilledButton(
                  onPressed: onBuy,
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(52),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  child: const Text(
                    'Buy Now',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  'Powered by Dodo Payments',
                  style: Theme.of(context)
                      .textTheme
                      .bodySmall
                      ?.copyWith(color: Colors.grey.shade600),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class BusyScreenView extends StatelessWidget {
  const BusyScreenView({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('Starting checkout…'),
          ],
        ),
      ),
    );
  }
}

class ResultScreenView extends StatelessWidget {
  const ResultScreenView({super.key, required this.result, required this.onHome});

  final CheckoutResult result;
  final VoidCallback onHome;

  @override
  Widget build(BuildContext context) {
    final (glyph, color, title) = switch (result.status) {
      CheckoutStatus.succeeded => ('✓', const Color(0xFF34C759), 'Payment successful'),
      CheckoutStatus.failed => ('✕', const Color(0xFFFF3B30), 'Payment failed'),
      CheckoutStatus.cancelled => ('↩', const Color(0xFFFF9500), 'Checkout cancelled'),
      CheckoutStatus.pending => ('⏱', const Color(0xFF007AFF), 'Payment pending'),
      CheckoutStatus.expired => ('⏳', const Color(0xFF007AFF), 'Session expired'),
    };
    final detail = result.paymentId ??
        (result.status == CheckoutStatus.pending
            ? "We'll confirm via webhook once it settles."
            : null);
    return _OutcomeScreen(
      glyph: glyph,
      color: color,
      title: title,
      detail: detail,
      detailIsMonospace: result.paymentId != null,
      onHome: onHome,
    );
  }
}

class ErrorScreenView extends StatelessWidget {
  const ErrorScreenView({super.key, required this.message, required this.onHome});

  final String message;
  final VoidCallback onHome;

  @override
  Widget build(BuildContext context) {
    return _OutcomeScreen(
      glyph: '⚠',
      color: const Color(0xFFFF9500),
      title: 'Something went wrong',
      detail: message,
      detailIsMonospace: false,
      onHome: onHome,
    );
  }
}

class _OutcomeScreen extends StatelessWidget {
  const _OutcomeScreen({
    required this.glyph,
    required this.color,
    required this.title,
    required this.detail,
    required this.detailIsMonospace,
    required this.onHome,
  });

  final String glyph;
  final Color color;
  final String title;
  final String? detail;
  final bool detailIsMonospace;
  final VoidCallback onHome;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(glyph, style: TextStyle(fontSize: 72, color: color)),
                const SizedBox(height: 20),
                Text(
                  title,
                  style: Theme.of(context)
                      .textTheme
                      .headlineSmall
                      ?.copyWith(fontWeight: FontWeight.bold),
                  textAlign: TextAlign.center,
                ),
                if (detail != null) ...[
                  const SizedBox(height: 12),
                  Text(
                    detail!,
                    textAlign: TextAlign.center,
                    style: (detailIsMonospace
                            ? Theme.of(context).textTheme.labelSmall?.copyWith(
                                  fontFamily: 'monospace',
                                )
                            : Theme.of(context).textTheme.bodyMedium)
                        ?.copyWith(color: Colors.grey.shade600),
                  ),
                ],
                const SizedBox(height: 32),
                FilledButton(
                  onPressed: onHome,
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(52),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  child: const Text(
                    'Go Home',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
