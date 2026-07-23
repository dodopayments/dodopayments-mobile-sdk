import 'package:dodopayments_checkout/dodopayments_checkout.dart';
import 'package:dodopayments_checkout/src/messages.g.dart';
import 'package:flutter/services.dart' show PlatformException;
import 'package:flutter_test/flutter_test.dart';

/// Fake host API standing in for the Pigeon platform channel, so the facade's
/// DTO <-> typed-surface mapping can be asserted without any platform code.
class _FakeHostApi extends DodoCheckoutHostApi {
  NativeCheckoutResult? startResult;
  PlatformException? startError;
  StartRequest? lastStartRequest;
  NativeAbandonedSession? abandonedSession;
  int clearCalls = 0;

  /// Called synchronously from [start], before it resolves — lets tests
  /// simulate native-pushed lifecycle events during the call.
  void Function()? onStartCalled;

  @override
  Future<NativeCheckoutResult> start(StartRequest request) async {
    lastStartRequest = request;
    onStartCalled?.call();
    // Real events arrive with real time gaps between them; give the
    // broadcast stream's queued microtasks a full event-loop tick to
    // deliver before this resolves and the caller unsubscribes.
    await Future<void>.delayed(Duration.zero);
    final PlatformException? error = startError;
    if (error != null) throw error;
    return startResult!;
  }

  @override
  Future<NativeAbandonedSession?> getAbandonedSession() async =>
      abandonedSession;

  @override
  Future<void> clearAbandonedSession() async {
    clearCalls += 1;
  }
}

void main() {
  late _FakeHostApi api;
  late DodoCheckout checkout;

  final CheckoutParams params = CheckoutParams(
    checkoutUrl: Uri.parse(
        'https://test.checkout.dodopayments.com/session/cks_test_123'),
    returnUrl: Uri.parse('myapp://checkout/return'),
  );

  NativeCheckoutResult nativeResult({
    NativeCheckoutStatus status = NativeCheckoutStatus.succeeded,
  }) =>
      NativeCheckoutResult(status: status, raw: <String, String>{});

  setUp(() {
    api = _FakeHostApi();
    checkout = DodoCheckout.forTesting(api);
  });

  group('start request mapping', () {
    test('passes URLs through', () async {
      api.startResult = nativeResult();
      await checkout.start(params);

      final StartRequest request = api.lastStartRequest!;
      expect(request.checkoutUrl,
          'https://test.checkout.dodopayments.com/session/cks_test_123');
      expect(request.returnUrl, 'myapp://checkout/return');
    });
  });

  group('event forwarding', () {
    test('forwards lifecycle events to onEvent', () async {
      api.startResult = nativeResult();
      api.onStartCalled = () {
        checkout.debugEmitEvent(
          NativeCheckoutEvent(type: NativeEventType.opened),
        );
        checkout.debugEmitEvent(
          NativeCheckoutEvent(
            type: NativeEventType.navigation,
            host: 'checkout.dodopayments.com',
          ),
        );
      };

      final List<CheckoutEventType> events = <CheckoutEventType>[];
      final List<String?> hosts = <String?>[];
      await checkout.start(CheckoutParams(
        checkoutUrl: params.checkoutUrl,
        returnUrl: params.returnUrl,
        onEvent: (CheckoutEvent e) {
          events.add(e.type);
          hosts.add(e.host);
        },
      ));

      expect(events, <CheckoutEventType>[
        CheckoutEventType.opened,
        CheckoutEventType.navigation,
      ]);
      expect(hosts, <String?>[null, 'checkout.dodopayments.com']);
    });

    test('events emitted after the call completes are not delivered',
        () async {
      api.startResult = nativeResult();
      await checkout.start(params); // no onEvent

      // Should not throw even with no active listener.
      checkout.debugEmitEvent(NativeCheckoutEvent(type: NativeEventType.opened));
    });
  });

  group('result mapping', () {
    test('status parity: every native status maps 1:1', () async {
      const Map<NativeCheckoutStatus, CheckoutStatus> expected =
          <NativeCheckoutStatus, CheckoutStatus>{
        NativeCheckoutStatus.succeeded: CheckoutStatus.succeeded,
        NativeCheckoutStatus.failed: CheckoutStatus.failed,
        NativeCheckoutStatus.cancelled: CheckoutStatus.cancelled,
        NativeCheckoutStatus.pending: CheckoutStatus.pending,
        NativeCheckoutStatus.expired: CheckoutStatus.expired,
      };
      // Guards against a new native status silently missing a mapping.
      expect(expected.length, NativeCheckoutStatus.values.length);
      expect(expected.values.toSet(), CheckoutStatus.values.toSet());

      for (final MapEntry<NativeCheckoutStatus, CheckoutStatus> entry
          in expected.entries) {
        api.startResult = nativeResult(status: entry.key);
        final CheckoutResult result = await checkout.start(params);
        expect(result.status, entry.value, reason: 'for ${entry.key}');
      }
    });

    test('maps all payload fields', () async {
      api.startResult = NativeCheckoutResult(
        status: NativeCheckoutStatus.succeeded,
        paymentId: 'pay_123',
        subscriptionId: 'sub_456',
        licenseKeys: <String>['lk_1', 'lk_2'],
        customerEmail: 'buyer@example.com',
        raw: <String, String>{
          'payment_id': 'pay_123',
          'status': 'succeeded',
          'email': 'buyer@example.com',
        },
      );

      final CheckoutResult result = await checkout.start(params);
      expect(result.paymentId, 'pay_123');
      expect(result.subscriptionId, 'sub_456');
      expect(result.licenseKeys, <String>['lk_1', 'lk_2']);
      expect(result.customerEmail, 'buyer@example.com');
      expect(result.raw['status'], 'succeeded');
      expect(result.raw['payment_id'], 'pay_123');
      // raw must be read-only for callers.
      expect(() => result.raw['x'] = 'y', throwsUnsupportedError);
    });

    test('optional fields stay null', () async {
      api.startResult = nativeResult(status: NativeCheckoutStatus.cancelled);
      final CheckoutResult result = await checkout.start(params);
      expect(result.paymentId, isNull);
      expect(result.subscriptionId, isNull);
      expect(result.licenseKeys, isNull);
      expect(result.customerEmail, isNull);
      expect(result.raw, isEmpty);
    });
  });

  group('error mapping', () {
    const Map<String, CheckoutErrorCode> wireToCode =
        <String, CheckoutErrorCode>{
      'INVALID_CHECKOUT_URL': CheckoutErrorCode.invalidCheckoutUrl,
      'INVALID_RETURN_URL': CheckoutErrorCode.invalidReturnUrl,
      'ALREADY_IN_PROGRESS': CheckoutErrorCode.alreadyInProgress,
      'PLATFORM_ERROR': CheckoutErrorCode.platformError,
    };

    test('covers every native error code', () {
      expect(wireToCode.values.toSet(), CheckoutErrorCode.values.toSet());
    });

    for (final MapEntry<String, CheckoutErrorCode> entry
        in wireToCode.entries) {
      test('PlatformException(${entry.key}) -> ${entry.value}', () async {
        api.startError =
            PlatformException(code: entry.key, message: 'native says no');
        await expectLater(
          checkout.start(params),
          throwsA(isA<CheckoutException>()
              .having((CheckoutException e) => e.code, 'code', entry.value)
              .having((CheckoutException e) => e.message, 'message',
                  'native says no')
              .having((CheckoutException e) => e.nativeCode, 'nativeCode',
                  entry.key)),
        );
      });
    }

    test('unknown code (e.g. Pigeon channel-error) -> platformError',
        () async {
      api.startError = PlatformException(code: 'channel-error');
      await expectLater(
        checkout.start(params),
        throwsA(isA<CheckoutException>().having(
            (CheckoutException e) => e.code,
            'code',
            CheckoutErrorCode.platformError)),
      );
    });

    test('message falls back to the code when native sends none', () async {
      api.startError = PlatformException(code: 'ALREADY_IN_PROGRESS');
      await expectLater(
        checkout.start(params),
        throwsA(isA<CheckoutException>().having(
            (CheckoutException e) => e.message,
            'message',
            'ALREADY_IN_PROGRESS')),
      );
    });
  });

  group('abandoned session', () {
    test('null passes through', () async {
      expect(await checkout.getAbandonedSession(), isNull);
    });

    test('maps sessionId and epoch millis to a UTC DateTime', () async {
      api.abandonedSession = NativeAbandonedSession(
        sessionId: 'cks_abandoned_1',
        createdAtMillis: 1721400000000,
      );
      final AbandonedSession? session = await checkout.getAbandonedSession();
      expect(session, isNotNull);
      expect(session!.sessionId, 'cks_abandoned_1');
      expect(session.createdAt,
          DateTime.fromMillisecondsSinceEpoch(1721400000000, isUtc: true));
      expect(session.createdAt.isUtc, isTrue);
    });

    test('clearAbandonedSession delegates to the host API', () async {
      await checkout.clearAbandonedSession();
      expect(api.clearCalls, 1);
    });
  });
}
