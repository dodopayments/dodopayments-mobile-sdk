import Foundation

#if canImport(UIKit)
import UIKit
#endif

// The Swift checkout core is vendored into ios/DodoCore/ (see the podspec) and
// compiled into this same pod target, so its public API — `DodoCheckout`,
// `CheckoutResult`, `CheckoutError`, etc. — is available directly here without
// an `import`.

/// Bridges React Native calls to the vendored Swift `DodoCheckout` core.
/// Contains no checkout logic; only translation between JS dictionaries and the
/// core's typed API. Wired to the codegen-generated spec by
/// `DodoCheckoutReactNative.mm`.
@objc(DodoCheckoutReactNativeImpl)
public final class DodoCheckoutReactNativeImpl: NSObject {

  /// Emits a lifecycle event to JS (the `.mm` sets this to `emitOnCheckoutEvent`).
  @objc public var eventEmitter: ((_ body: [String: Any]) -> Void)?

  @objc(startWithParams:resolve:reject:)
  public func start(
    _ params: NSDictionary,
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String, String, Error?) -> Void
  ) {
    guard
      let checkoutUrlString = params["checkoutUrl"] as? String,
      let checkoutUrl = URL(string: checkoutUrlString)
    else {
      reject("INVALID_CHECKOUT_URL", "checkoutUrl is missing or not a URL.", nil)
      return
    }
    guard
      let returnUrlString = params["returnUrl"] as? String,
      let returnUrl = URL(string: returnUrlString)
    else {
      reject("INVALID_RETURN_URL", "returnUrl is missing or not a URL.", nil)
      return
    }

    let customization = Self.customization(
      fromJson: params["customizationJson"] as? String
    )

    Task { @MainActor in
      do {
        let result = try await DodoCheckout.start(
          checkoutUrl: checkoutUrl,
          returnUrl: returnUrl,
          customization: customization,
          onEvent: { [weak self] event in
            self?.forward(event)
          }
        )
        resolve(Self.serialize(result))
      } catch let error as CheckoutError {
        reject(error.code.rawValue, error.message, error)
      } catch {
        reject("PLATFORM_ERROR", error.localizedDescription, error)
      }
    }
  }

  @objc(getAbandonedSessionWithResolve:reject:)
  public func getAbandonedSession(
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String, String, Error?) -> Void
  ) {
    guard let session = DodoCheckout.getAbandonedSession() else {
      resolve(nil)
      return
    }
    resolve([
      "sessionId": session.sessionId,
      "createdAt": session.createdAt.timeIntervalSince1970
    ])
  }

  @objc(clearAbandonedSessionWithResolve:reject:)
  public func clearAbandonedSession(
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String, String, Error?) -> Void
  ) {
    DodoCheckout.clearAbandonedSession()
    resolve(nil)
  }

  @objc(handleOpenURLWithUrl:resolve:reject:)
  public func handleOpenURL(
    _ urlString: String,
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String, String, Error?) -> Void
  ) {
    guard let url = URL(string: urlString) else {
      resolve(false)
      return
    }
    Task { @MainActor in
      resolve(DodoCheckout.handleOpenURL(url))
    }
  }

  // MARK: - Mapping

  private func forward(_ event: CheckoutEvent) {
    eventEmitter?(["type": event.name])
  }

  private static func serialize(_ result: CheckoutResult) -> [String: Any] {
    var body: [String: Any] = [
      "status": result.status.rawValue,
      "raw": result.raw
    ]
    if let paymentId = result.paymentId { body["paymentId"] = paymentId }
    if let subscriptionId = result.subscriptionId { body["subscriptionId"] = subscriptionId }
    if let email = result.customerEmail { body["customerEmail"] = email }
    if let keys = result.licenseKeys { body["licenseKeys"] = keys }
    return body
  }

  // JSON-decoded from the `customizationJson` string param — see the
  // note on `NativeCheckoutParams.customizationJson` in the JS layer
  // for why this crosses the bridge as a JSON string rather than a typed
  // nested object. Only the "ios" sub-object is read; "android" (if present)
  // is for the Android native module, not this one. `nil` (an
  // absent/unrecognized key) is passed straight through to
  // BrowserCustomization rather than resolved to a fallback here — the core
  // itself decides what "unset" means (usually: don't touch the platform's
  // own setter at all).
  private static func customization(fromJson json: String?) -> BrowserCustomization {
    guard
      let json,
      let data = json.data(using: .utf8),
      let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    else {
      return BrowserCustomization()
    }
    let ios = dict["ios"] as? [String: Any]
    return BrowserCustomization(
      dismissButtonStyle: {
        switch ios?["dismissButtonStyle"] as? String {
        case "close": return .close
        case "cancel": return .cancel
        case "done": return .done
        default: return nil
        }
      }(),
      barCollapsingEnabled: ios?["barCollapsingEnabled"] as? Bool,
      presentationStyle: {
        switch ios?["presentationStyle"] as? String {
        case "fullScreen": return .fullScreen
        case "pageSheet": return .pageSheet
        default: return nil
        }
      }(),
      colorScheme: {
        switch ios?["colorScheme"] as? String {
        case "light": return .light
        case "dark": return .dark
        case "system": return .system
        default: return nil
        }
      }()
    )
  }
}
