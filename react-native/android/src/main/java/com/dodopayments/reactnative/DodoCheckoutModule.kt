package com.dodopayments.reactnative

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.dodopayments.checkout.CheckoutError
import com.dodopayments.checkout.CheckoutEvent
import com.dodopayments.checkout.CheckoutParams
import com.dodopayments.checkout.CheckoutResult
import com.dodopayments.checkout.DodoCheckout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * React Native TurboModule bridge. Contains no checkout logic — it forwards to
 * `com.dodopayments:checkout-android` and maps the result back to JS.
 *
 * Extends the codegen-generated `NativeDodoCheckoutSpec` (New Architecture only).
 * Events use the codegen EventEmitter (`emitOnCheckoutEvent`).
 */
class DodoCheckoutModule(reactContext: ReactApplicationContext) :
  NativeDodoCheckoutSpec(reactContext) {

  private val scope = CoroutineScope(Dispatchers.Main)

  override fun getName(): String = NAME

  override fun start(params: ReadableMap, promise: Promise) {
    val activity = reactApplicationContext.currentActivity
    if (activity == null) {
      promise.reject("PLATFORM_ERROR", "No current activity to present the checkout.")
      return
    }

    val checkoutParams = CheckoutParams(
      checkoutUrl = params.getString("checkoutUrl") ?: "",
      returnUrl = params.getString("returnUrl") ?: "",
    )

    scope.launch {
      try {
        val result = DodoCheckout.start(activity, checkoutParams) { event ->
          emitEvent(event)
        }
        promise.resolve(result.toWritableMap())
      } catch (error: CheckoutError) {
        promise.reject(error.code.name, error.message, error)
      } catch (t: Throwable) {
        promise.reject("PLATFORM_ERROR", t.message, t)
      }
    }
  }

  override fun getAbandonedSession(promise: Promise) {
    val session = DodoCheckout.getAbandonedSession(reactApplicationContext)
    if (session == null) {
      promise.resolve(null)
      return
    }
    val map = Arguments.createMap().apply {
      putString("sessionId", session.sessionId)
      // createdAt is epoch milliseconds (Long); JS expects epoch seconds.
      putDouble("createdAt", session.createdAt / 1000.0)
    }
    promise.resolve(map)
  }

  override fun clearAbandonedSession(promise: Promise) {
    DodoCheckout.clearAbandonedSession(reactApplicationContext)
    promise.resolve(null)
  }

  // No-op on Android: the browser surface (Custom Tabs + BrowserRedirectActivity)
  // is fully self-contained via Activities, unlike iOS's SFSafariViewController.
  override fun handleOpenURL(url: String, promise: Promise) {
    promise.resolve(false)
  }

  private fun emitEvent(event: CheckoutEvent) {
    val payload = Arguments.createMap().apply {
      putString("type", event.name)
    }
    emitOnCheckoutEvent(payload)
  }

  private fun CheckoutResult.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    map.putString("status", status.name.lowercase())
    paymentId?.let { map.putString("paymentId", it) }
    subscriptionId?.let { map.putString("subscriptionId", it) }
    customerEmail?.let { map.putString("customerEmail", it) }
    licenseKeys?.let { keys ->
      val array = Arguments.createArray()
      keys.forEach { array.pushString(it) }
      map.putArray("licenseKeys", array)
    }
    val rawMap = Arguments.createMap()
    raw.forEach { (k, v) -> rawMap.putString(k, v) }
    map.putMap("raw", rawMap)
    return map
  }

  companion object {
    const val NAME = "DodoCheckout"
  }
}
