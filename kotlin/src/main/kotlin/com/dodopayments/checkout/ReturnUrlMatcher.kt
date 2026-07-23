package com.dodopayments.checkout

/**
 * Decides whether a navigation is the merchant's `return_url`.
 *
 * The match compares **scheme + host + path only** and ignores the query and
 * fragment — the query string is the payload, not part of the identity. Host
 * and scheme are compared case-insensitively and a single trailing slash on
 * the path is ignored, so cosmetic differences still match.
 *
 * Crucially, the intermediate `…/return/{payment_id}` hop on Dodo's own host
 * must **not** match — only the final merchant `return_url` does. Because the
 * merchant's return URL host differs from the Dodo backend host, comparing the
 * host guarantees this.
 */
internal class ReturnUrlMatcher(returnUrl: String) {
    val scheme: String
    val host: String
    val path: String

    init {
        val parts = UrlParts.from(returnUrl)
        scheme = normalizeScheme(parts.scheme)
        host = normalizeHost(parts.host)
        path = normalizePath(parts.path)
    }

    fun matches(url: String): Boolean {
        val parts = UrlParts.from(url)
        return normalizeScheme(parts.scheme) == scheme &&
            normalizeHost(parts.host) == host &&
            normalizePath(parts.path) == path
    }

    private companion object {
        fun normalizeScheme(scheme: String?): String = scheme?.lowercase() ?: ""

        fun normalizeHost(host: String?): String = host?.lowercase() ?: ""

        /** Treats `/done` and `/done/` as equal; an empty path normalizes to `/`. */
        fun normalizePath(path: String?): String {
            var p = path ?: ""
            if (p.isEmpty()) return "/"
            while (p.length > 1 && p.endsWith("/")) {
                p = p.dropLast(1)
            }
            return p
        }
    }
}
