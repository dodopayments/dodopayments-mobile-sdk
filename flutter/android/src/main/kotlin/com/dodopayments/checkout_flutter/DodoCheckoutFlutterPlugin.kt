package com.dodopayments.checkout_flutter

import android.app.Activity
import android.content.Context
import com.dodopayments.checkout.CheckoutError
import com.dodopayments.checkout.CheckoutEvent
import com.dodopayments.checkout.CheckoutParams
import com.dodopayments.checkout.CheckoutStatus
import com.dodopayments.checkout.DodoCheckout
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Thin bridge between the Pigeon-generated [DodoCheckoutHostApi] and the
 * native Android core (`com.dodopayments:checkout-android`). No checkout
 * logic lives here — validation, the Custom Tab launch, return interception
 * and result parsing all happen in the core.
 */
class DodoCheckoutFlutterPlugin : FlutterPlugin, ActivityAware, DodoCheckoutHostApi {

  private var applicationContext: Context? = null
  private var activity: Activity? = null
  private var flutterApi: DodoCheckoutFlutterApi? = null
  private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  // -- FlutterPlugin ---------------------------------------------------------

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    applicationContext = binding.applicationContext
    flutterApi = DodoCheckoutFlutterApi(binding.binaryMessenger)
    DodoCheckoutHostApi.setUp(binding.binaryMessenger, this)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    DodoCheckoutHostApi.setUp(binding.binaryMessenger, null)
    flutterApi = null
    mainScope.cancel()
    applicationContext = null
  }

  // -- ActivityAware ---------------------------------------------------------

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  // -- DodoCheckoutHostApi ---------------------------------------------------

  override fun start(request: StartRequest, callback: (Result<NativeCheckoutResult>) -> Unit) {
    val activity = this.activity
    if (activity == null) {
      callback(
          Result.failure(
              FlutterError(
                  "PLATFORM_ERROR",
                  "No foreground Activity to present the checkout from.",
                  null)))
      return
    }
    mainScope.launch {
      try {
        val result =
            DodoCheckout.start(
                activity,
                CheckoutParams(
                    checkoutUrl = request.checkoutUrl,
                    returnUrl = request.returnUrl)) { event ->
              flutterApi?.onCheckoutEvent(event.toNative()) {}
            }
        callback(Result.success(result.toNative()))
      } catch (error: CheckoutError) {
        // The core's error-code enum constants are named exactly like the wire
        // codes (INVALID_CHECKOUT_URL, ...), so `name` is the wire string.
        callback(Result.failure(FlutterError(error.code.name, error.message, null)))
      } catch (error: Throwable) {
        callback(
            Result.failure(FlutterError("PLATFORM_ERROR", error.message ?: error.toString(), null)))
      }
    }
  }

  override fun getAbandonedSession(): NativeAbandonedSession? {
    val context =
        applicationContext
            ?: throw FlutterError("PLATFORM_ERROR", "Plugin is not attached to an engine.", null)
    val session = DodoCheckout.getAbandonedSession(context) ?: return null
    return NativeAbandonedSession(
        sessionId = session.sessionId,
        createdAtMillis = session.createdAt)
  }

  override fun clearAbandonedSession() {
    val context =
        applicationContext
            ?: throw FlutterError("PLATFORM_ERROR", "Plugin is not attached to an engine.", null)
    DodoCheckout.clearAbandonedSession(context)
  }

  // No-op on Android: the Custom Tab + BrowserRedirectActivity surface is
  // fully self-contained via Activities, unlike iOS's SFSafariViewController.
  override fun handleOpenURL(url: String): Boolean = false
}

private fun CheckoutEvent.toNative(): NativeCheckoutEvent {
  return when (this) {
    is CheckoutEvent.Opened -> NativeCheckoutEvent(type = NativeEventType.OPENED)
    is CheckoutEvent.ReturnReceived ->
        NativeCheckoutEvent(type = NativeEventType.RETURN_RECEIVED)
    is CheckoutEvent.Closed -> NativeCheckoutEvent(type = NativeEventType.CLOSED)
  }
}

private fun com.dodopayments.checkout.CheckoutResult.toNative(): NativeCheckoutResult {
  return NativeCheckoutResult(
      status =
          when (status) {
            CheckoutStatus.SUCCEEDED -> NativeCheckoutStatus.SUCCEEDED
            CheckoutStatus.FAILED -> NativeCheckoutStatus.FAILED
            CheckoutStatus.CANCELLED -> NativeCheckoutStatus.CANCELLED
            CheckoutStatus.PENDING -> NativeCheckoutStatus.PENDING
            CheckoutStatus.EXPIRED -> NativeCheckoutStatus.EXPIRED
          },
      paymentId = paymentId,
      subscriptionId = subscriptionId,
      licenseKeys = licenseKeys,
      customerEmail = customerEmail,
      raw = raw)
}
