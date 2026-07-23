package com.dodopayments.checkout

import java.net.URI
import java.net.URISyntaxException

/**
 * Tolerant URL decomposition used by all pure logic (matcher, parser,
 * validator, router). Uses [java.net.URI] rather than `android.net.Uri` so the
 * logic is unit-testable on a plain JVM.
 *
 * Some real-world navigation targets (notably `intent://…;end` URLs) can fail
 * strict URI parsing; in that case the scheme is still extracted textually so
 * the navigation router can route them externally.
 */
internal data class UrlParts(
    val scheme: String?,
    val host: String?,
    val path: String?,
    val rawQuery: String?
) {
    companion object {
        private val SCHEME_REGEX = Regex("^([a-zA-Z][a-zA-Z0-9+.\\-]*):")

        fun from(url: String): UrlParts = try {
            val uri = URI(url)
            UrlParts(uri.scheme, uri.host, uri.path, uri.rawQuery)
        } catch (_: URISyntaxException) {
            UrlParts(SCHEME_REGEX.find(url)?.groupValues?.get(1), null, null, null)
        }
    }
}
