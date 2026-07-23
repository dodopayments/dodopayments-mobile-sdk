package com.dodopayments.checkout

import java.net.URLDecoder

/**
 * Turns the query string on a matched `return_url` into a [CheckoutResult].
 *
 * Status mapping (see plan §3):
 *   - `succeeded` (one-time) / `active` (subscription)  → [CheckoutStatus.SUCCEEDED]
 *   - `processing` / any `requires_*`                   → [CheckoutStatus.PENDING]
 *   - `failed`                                          → [CheckoutStatus.FAILED]
 *   - `expired`                                         → [CheckoutStatus.EXPIRED]
 *   - anything else (or missing)                        → [CheckoutStatus.PENDING]
 *
 * An unrecognized/missing status defaults to PENDING rather than a definite
 * outcome: the SDK result is a UI hint and the webhook is the authority, so
 * "we don't know yet" is the safe fallback.
 */
internal object ResultParser {

    fun parse(url: String): CheckoutResult {
        val rawQuery = UrlParts.from(url).rawQuery
        val raw = LinkedHashMap<String, String>()
        val licenseKeys = mutableListOf<String>()

        if (!rawQuery.isNullOrEmpty()) {
            for (pair in rawQuery.split("&")) {
                if (pair.isEmpty()) continue
                val idx = pair.indexOf('=')
                val name = decode(if (idx >= 0) pair.substring(0, idx) else pair)
                val value = if (idx >= 0) decode(pair.substring(idx + 1)) else ""
                raw[name] = value
                if (name == "license_key" && value.isNotEmpty()) {
                    licenseKeys.add(value)
                }
            }
        }

        return CheckoutResult(
            status = mapStatus(raw["status"]),
            paymentId = nonEmpty(raw["payment_id"]),
            subscriptionId = nonEmpty(raw["subscription_id"]),
            licenseKeys = licenseKeys.ifEmpty { null },
            customerEmail = nonEmpty(raw["email"]),
            raw = raw
        )
    }

    fun mapStatus(rawStatus: String?): CheckoutStatus {
        val raw = rawStatus?.lowercase()
        if (raw.isNullOrEmpty()) return CheckoutStatus.PENDING
        return when (raw) {
            "succeeded", "active" -> CheckoutStatus.SUCCEEDED
            "failed" -> CheckoutStatus.FAILED
            "expired" -> CheckoutStatus.EXPIRED
            "processing" -> CheckoutStatus.PENDING
            else ->
                // `requires_action`, `requires_payment_method`, and any future
                // async state settle later — treat as pending.
                CheckoutStatus.PENDING
        }
    }

    /**
     * Percent-decodes a query component. A literal `+` is preserved (matching
     * the iOS SDK's `URLComponents` semantics) rather than decoded to a space.
     */
    private fun decode(component: String): String = try {
        URLDecoder.decode(component.replace("+", "%2B"), "UTF-8")
    } catch (_: IllegalArgumentException) {
        component
    }

    private fun nonEmpty(value: String?): String? = value?.takeIf { it.isNotEmpty() }
}
