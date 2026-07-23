import Flutter
import UIKit

// NOTE: There is deliberately no `import DodoCheckout` here. The podspec
// compiles the iOS core's Swift sources (swift/Sources/DodoCheckout) into this
// same pod, so `DodoCheckout`, `CheckoutError`, ... are
// members of this module. See dodopayments_checkout.podspec.

/// Thin bridge between the Pigeon-generated `DodoCheckoutHostApi` and the
/// iOS core. No checkout logic lives here — validation, the
/// SFSafariViewController sheet, return interception and result parsing all
/// happen in the core.
public class DodoCheckoutFlutterPlugin: NSObject, FlutterPlugin {
  private var flutterApi: DodoCheckoutFlutterApi?

  public static func register(with registrar: FlutterPluginRegistrar) {
    let plugin = DodoCheckoutFlutterPlugin()
    plugin.flutterApi = DodoCheckoutFlutterApi(binaryMessenger: registrar.messenger())
    DodoCheckoutHostApiSetup.setUp(binaryMessenger: registrar.messenger(), api: plugin)
    registrar.publish(plugin)
  }
}

extension DodoCheckoutFlutterPlugin: DodoCheckoutHostApi {
  func start(
    request: StartRequest, completion: @escaping (Result<NativeCheckoutResult, Error>) -> Void
  ) {
    // URL parse failures surface the same codes the core uses for invalid
    // input; the core re-validates (host allow-list, /session/ path, ...).
    guard let checkoutUrl = URL(string: request.checkoutUrl) else {
      completion(
        .failure(
          PigeonError(
            code: CheckoutError.Code.invalidCheckoutUrl.rawValue,
            message: "checkoutUrl is not a well-formed URL.",
            details: nil)))
      return
    }
    guard let returnUrl = URL(string: request.returnUrl) else {
      completion(
        .failure(
          PigeonError(
            code: CheckoutError.Code.invalidReturnUrl.rawValue,
            message: "returnUrl is not a well-formed URL.",
            details: nil)))
      return
    }

    // `completion` is Pigeon-generated and not `Sendable`. `@unchecked
    // Sendable`-boxing it hits a region-based isolation checker limitation
    // ("please file a bug") — `nonisolated(unsafe)` is the direct fix; it's
    // called exactly once, so there's no actual race.
    nonisolated(unsafe) let completion = completion
    // `DodoCheckoutFlutterApi` isn't Sendable, but `onEvent` is always
    // invoked from the core's `@MainActor`-isolated code, and Flutter's
    // platform channel calls must happen on the main thread anyway — no
    // actual race, same reasoning as `completion` above.
    nonisolated(unsafe) let flutterApi = self.flutterApi
    Task {
      do {
        let result = try await DodoCheckout.start(
          checkoutUrl: checkoutUrl,
          returnUrl: returnUrl,
          onEvent: { event in
            flutterApi?.onCheckoutEvent(event: Self.toNative(event)) { _ in }
          }
        )
        completion(.success(Self.toNative(result)))
      } catch let error as CheckoutError {
        completion(
          .failure(PigeonError(code: error.code.rawValue, message: error.message, details: nil)))
      } catch {
        completion(
          .failure(
            PigeonError(
              code: CheckoutError.Code.platformError.rawValue,
              message: String(describing: error),
              details: nil)))
      }
    }
  }

  func getAbandonedSession() throws -> NativeAbandonedSession? {
    guard let session = DodoCheckout.getAbandonedSession() else { return nil }
    return NativeAbandonedSession(
      sessionId: session.sessionId,
      createdAtMillis: Int64((session.createdAt.timeIntervalSince1970 * 1000).rounded())
    )
  }

  func clearAbandonedSession() throws {
    DodoCheckout.clearAbandonedSession()
  }

  func handleOpenURL(url: String) throws -> Bool {
    guard let parsedUrl = URL(string: url) else { return false }
    // Flutter invokes platform-channel handlers on the main thread already;
    // `assumeIsolated` bridges into `DodoCheckout.handleOpenURL`'s
    // `@MainActor` isolation without an async hop this sync Pigeon method
    // signature can't express.
    return MainActor.assumeIsolated {
      DodoCheckout.handleOpenURL(parsedUrl)
    }
  }

  // -- Mapping helpers -------------------------------------------------------

  private static func toNative(_ event: CheckoutEvent) -> NativeCheckoutEvent {
    switch event {
    case .opened:
      return NativeCheckoutEvent(type: .opened, host: nil)
    case .navigation(let host):
      return NativeCheckoutEvent(type: .navigation, host: host)
    case .returnReceived:
      return NativeCheckoutEvent(type: .returnReceived, host: nil)
    case .closed:
      return NativeCheckoutEvent(type: .closed, host: nil)
    }
  }

  private static func toNative(_ result: CheckoutResult) -> NativeCheckoutResult {
    let status: NativeCheckoutStatus
    switch result.status {
    case .succeeded: status = .succeeded
    case .failed: status = .failed
    case .cancelled: status = .cancelled
    case .pending: status = .pending
    case .expired: status = .expired
    }
    return NativeCheckoutResult(
      status: status,
      paymentId: result.paymentId,
      subscriptionId: result.subscriptionId,
      licenseKeys: result.licenseKeys,
      customerEmail: result.customerEmail,
      raw: result.raw
    )
  }
}
