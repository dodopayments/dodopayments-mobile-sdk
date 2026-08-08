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
    let customization = Self.toCore(request.customization)

    Task {
      do {
        let result = try await DodoCheckout.start(
          checkoutUrl: checkoutUrl,
          returnUrl: returnUrl,
          customization: customization,
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

  // Only the "ios" sub-object is read — "android" (if present) is for the
  // Android plugin's own mapping, not this one. `nil` (absent) is passed
  // straight through to BrowserCustomization rather than resolved to a
  // fallback here — the core itself decides what "unset" means (usually:
  // don't touch the corresponding SFSafariViewController property at all).
  private static func toCore(_ native: NativeBrowserCustomization?) -> BrowserCustomization {
    guard let native else { return BrowserCustomization() }
    let ios = native.ios
    return BrowserCustomization(
      dismissButtonStyle: {
        switch ios?.dismissButtonStyle {
        case .close: return .close
        case .cancel: return .cancel
        case .done: return .done
        case nil: return nil
        }
      }(),
      barCollapsingEnabled: ios?.barCollapsingEnabled,
      presentationStyle: {
        switch ios?.presentationStyle {
        case .fullScreen: return .fullScreen
        case .pageSheet: return .pageSheet
        case nil: return nil
        }
      }(),
      colorScheme: {
        switch ios?.colorScheme {
        case .light: return .light
        case .dark: return .dark
        case .system: return .system
        case nil: return nil
        }
      }()
    )
  }

  private static func toNative(_ event: CheckoutEvent) -> NativeCheckoutEvent {
    switch event {
    case .opened:
      return NativeCheckoutEvent(type: .opened)
    case .returnReceived:
      return NativeCheckoutEvent(type: .returnReceived)
    case .closed:
      return NativeCheckoutEvent(type: .closed)
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
