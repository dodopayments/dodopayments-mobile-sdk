package com.dodopayments.reactnative

import android.graphics.Color
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.dodopayments.checkout.BrowserCustomization
import com.dodopayments.checkout.CheckoutError
import com.dodopayments.checkout.CheckoutEvent
import com.dodopayments.checkout.CheckoutParams
import com.dodopayments.checkout.CheckoutResult
import com.dodopayments.checkout.DodoCheckout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

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
      customization = params.getString("customizationJson")
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        .toBrowserCustomization(),
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

// JSON-decoded from the `customizationJson` string param — see the
// note on `NativeCheckoutParams.customizationJson` in the JS layer
// for why this crosses the bridge as a JSON string rather than a typed
// nested object. Only the "android" sub-object is read; "ios" (if present)
// is for the iOS native module, not this one.
private fun JSONObject.optStringOrNull(key: String): String? =
  if (has(key) && !isNull(key)) getString(key) else null

// getBoolean() throws JSONException for a present-but-wrong-typed value (e.g.
// a string or number from plain JS, not just TypeScript callers) — caught
// here rather than left to escape start() before the try/catch that would
// otherwise turn it into a clean promise rejection.
private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
  if (has(key) && !isNull(key)) runCatching { getBoolean(key) }.getOrNull() else null

// `null` (an absent/unrecognized key) is passed straight through to
// BrowserCustomization rather than resolved to a fallback here — the core
// itself decides what "unset" means (usually: don't touch the platform's
// own setter at all).
private fun JSONObject?.toBrowserCustomization(): BrowserCustomization {
  if (this == null) return BrowserCustomization()
  val android = optJSONObject("android")
  return BrowserCustomization(
    toolbarColor = android?.optStringOrNull("toolbarColor")?.let(::parseColorOrNull),
    navigationBarColor = android?.optStringOrNull("navigationBarColor")?.let(::parseColorOrNull),
    navigationBarDividerColor = android?.optStringOrNull("navigationBarDividerColor")?.let(::parseColorOrNull),
    closeButtonStyle = when (android?.optStringOrNull("closeButtonStyle")) {
      "back" -> BrowserCustomization.CloseButtonStyle.BACK
      "default" -> BrowserCustomization.CloseButtonStyle.DEFAULT
      else -> null
    },
    closeButtonPosition = when (android?.optStringOrNull("closeButtonPosition")) {
      "end" -> BrowserCustomization.CloseButtonPosition.END
      "start" -> BrowserCustomization.CloseButtonPosition.START
      else -> null
    },
    shareButtonEnabled = android?.optBooleanOrNull("shareButtonEnabled"),
    showTitleEnabled = android?.optBooleanOrNull("showTitleEnabled"),
    urlBarHidingEnabled = android?.optBooleanOrNull("urlBarHidingEnabled"),
    bookmarksButtonEnabled = android?.optBooleanOrNull("bookmarksButtonEnabled"),
    downloadsButtonEnabled = android?.optBooleanOrNull("downloadsButtonEnabled"),
    colorScheme = when (android?.optStringOrNull("colorScheme")) {
      "light" -> BrowserCustomization.ColorScheme.LIGHT
      "dark" -> BrowserCustomization.ColorScheme.DARK
      "system" -> BrowserCustomization.ColorScheme.SYSTEM
      else -> null
    },
  )
}

private fun parseColorOrNull(hex: String): Int? =
  try {
    Color.parseColor(hex)
  } catch (e: IllegalArgumentException) {
    null
  }
