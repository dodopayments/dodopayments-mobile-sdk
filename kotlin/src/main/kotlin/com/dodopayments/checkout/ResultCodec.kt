package com.dodopayments.checkout

import android.content.Intent
import android.os.Bundle

/**
 * Encodes a [CheckoutResult] (or a [CheckoutError]) into the activity-result
 * Intent and back. Kept as plain extras so the result also survives process
 * death on the launcher-style path.
 */
internal object ResultCodec {

    private const val EXTRA_STATUS = "com.dodopayments.checkout.result.status"
    private const val EXTRA_PAYMENT_ID = "com.dodopayments.checkout.result.paymentId"
    private const val EXTRA_SUBSCRIPTION_ID = "com.dodopayments.checkout.result.subscriptionId"
    private const val EXTRA_LICENSE_KEYS = "com.dodopayments.checkout.result.licenseKeys"
    private const val EXTRA_CUSTOMER_EMAIL = "com.dodopayments.checkout.result.customerEmail"
    private const val EXTRA_RAW = "com.dodopayments.checkout.result.raw"
    private const val EXTRA_ERROR_CODE = "com.dodopayments.checkout.error.code"
    private const val EXTRA_ERROR_MESSAGE = "com.dodopayments.checkout.error.message"

    fun encode(result: CheckoutResult): Intent = Intent().apply {
        putExtra(EXTRA_STATUS, result.status.name)
        result.paymentId?.let { putExtra(EXTRA_PAYMENT_ID, it) }
        result.subscriptionId?.let { putExtra(EXTRA_SUBSCRIPTION_ID, it) }
        result.licenseKeys?.let { putStringArrayListExtra(EXTRA_LICENSE_KEYS, ArrayList(it)) }
        result.customerEmail?.let { putExtra(EXTRA_CUSTOMER_EMAIL, it) }
        val rawBundle = Bundle()
        for ((key, value) in result.raw) {
            rawBundle.putString(key, value)
        }
        putExtra(EXTRA_RAW, rawBundle)
    }

    fun decode(intent: Intent): CheckoutResult? {
        val statusName = intent.getStringExtra(EXTRA_STATUS) ?: return null
        val status = CheckoutStatus.entries.firstOrNull { it.name == statusName } ?: return null
        val rawBundle = intent.getBundleExtra(EXTRA_RAW)
        val raw = LinkedHashMap<String, String>()
        rawBundle?.keySet()?.forEach { key ->
            raw[key] = rawBundle.getString(key) ?: ""
        }
        return CheckoutResult(
            status = status,
            paymentId = intent.getStringExtra(EXTRA_PAYMENT_ID),
            subscriptionId = intent.getStringExtra(EXTRA_SUBSCRIPTION_ID),
            licenseKeys = intent.getStringArrayListExtra(EXTRA_LICENSE_KEYS)?.toList(),
            customerEmail = intent.getStringExtra(EXTRA_CUSTOMER_EMAIL),
            raw = raw
        )
    }

    fun encodeError(error: CheckoutError): Intent = Intent().apply {
        putExtra(EXTRA_ERROR_CODE, error.code.name)
        putExtra(EXTRA_ERROR_MESSAGE, error.message)
    }

    fun decodeError(intent: Intent): CheckoutError? {
        val codeName = intent.getStringExtra(EXTRA_ERROR_CODE) ?: return null
        val code = CheckoutError.Code.entries.firstOrNull { it.name == codeName } ?: return null
        return CheckoutError(code, intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: code.name)
    }
}
