package com.dodopayments.checkout

/**
 * Customizes the Custom Tab's chrome — colors, share button, overflow
 * items, and the close button. Every field is `null` by default, and `null`
 * means exactly that: the corresponding `CustomTabsIntent.Builder` setter is
 * never called at all, so the Custom Tab host's own live behavior applies —
 * not a value this SDK asserts on its behalf. This is deliberate: hardcoding
 * a guess at "the platform default" can go stale the moment the host app
 * (e.g. Chrome) changes it, silently changing behavior for every integrator
 * who never touched that field.
 *
 * New fields must be appended at the end, never inserted — this is a
 * `data class` with every field defaulted, so an insertion would both
 * silently shift the generated `componentN()` destructuring positions for
 * every field after it and drop the `@JvmOverloads`-generated constructor
 * overloads that came after the insertion point.
 */
data class BrowserCustomization @JvmOverloads constructor(
    /** Toolbar background color, as an ARGB [android.graphics.Color] int. */
    val toolbarColor: Int? = null,
    val navigationBarColor: Int? = null,
    val navigationBarDividerColor: Int? = null,
    val closeButtonStyle: CloseButtonStyle? = null,
    val closeButtonPosition: CloseButtonPosition? = null,
    /** The toolbar's share icon. `false` hides it entirely. */
    val shareButtonEnabled: Boolean? = null,
    /** Shows the page title under the URL in the toolbar. */
    val showTitleEnabled: Boolean? = null,
    /** Lets the toolbar auto-hide as the page scrolls. */
    val urlBarHidingEnabled: Boolean? = null,
    /** The overflow menu's "Bookmark this page" item. */
    val bookmarksButtonEnabled: Boolean? = null,
    /** The overflow menu's "Download page" item. */
    val downloadsButtonEnabled: Boolean? = null,
    /** Forces the Custom Tab's light/dark appearance regardless of the
     * system setting. */
    val colorScheme: ColorScheme? = null,
) {
    /**
     * `DEFAULT` is the system "X" icon (reads as "this is a modal").
     * `BACK` is a back-arrow the SDK draws itself, via [CloseButtonIcons]
     * (reads as "part of your app's flow") — Android ships no built-in
     * alternative to the "X".
     */
    enum class CloseButtonStyle { DEFAULT, BACK }
    enum class CloseButtonPosition { START, END }
    enum class ColorScheme { SYSTEM, LIGHT, DARK }
}

/**
 * Pure string-keyed encoding of [BrowserCustomization], so it can cross an
 * `Intent`/`Bundle` boundary (and survive process-death recreation) without
 * requiring `Parcelable` — same rationale as [KeyValueStore] for
 * [AbandonedSessionStore]. Kept free of `android.os.Bundle` itself so the
 * mapping stays unit-testable on the plain JVM; [BrowserCheckoutHostActivity]
 * does the thin `Bundle <-> Map` adaptation at the actual Intent boundary.
 *
 * Unset (`null`) fields are simply omitted from the map, so
 * [toBrowserCustomization] round-trips them back to `null` rather than to
 * some hardcoded fallback.
 */
internal fun BrowserCustomization.toStringMap(): Map<String, String> = buildMap {
    toolbarColor?.let { put("toolbarColor", it.toString()) }
    navigationBarColor?.let { put("navigationBarColor", it.toString()) }
    navigationBarDividerColor?.let { put("navigationBarDividerColor", it.toString()) }
    closeButtonStyle?.let { put("closeButtonStyle", it.name) }
    closeButtonPosition?.let { put("closeButtonPosition", it.name) }
    shareButtonEnabled?.let { put("shareButtonEnabled", it.toString()) }
    showTitleEnabled?.let { put("showTitleEnabled", it.toString()) }
    urlBarHidingEnabled?.let { put("urlBarHidingEnabled", it.toString()) }
    bookmarksButtonEnabled?.let { put("bookmarksButtonEnabled", it.toString()) }
    downloadsButtonEnabled?.let { put("downloadsButtonEnabled", it.toString()) }
    colorScheme?.let { put("colorScheme", it.name) }
}

/** Inverse of [toStringMap]. Missing or unrecognized entries decode to `null`. */
internal fun Map<String, String>.toBrowserCustomization(): BrowserCustomization =
    BrowserCustomization(
        toolbarColor = this["toolbarColor"]?.toIntOrNull(),
        navigationBarColor = this["navigationBarColor"]?.toIntOrNull(),
        navigationBarDividerColor = this["navigationBarDividerColor"]?.toIntOrNull(),
        closeButtonStyle = this["closeButtonStyle"]
            ?.let { name -> BrowserCustomization.CloseButtonStyle.entries.find { it.name == name } },
        closeButtonPosition = this["closeButtonPosition"]
            ?.let { name -> BrowserCustomization.CloseButtonPosition.entries.find { it.name == name } },
        shareButtonEnabled = this["shareButtonEnabled"]?.toBooleanStrictOrNull(),
        showTitleEnabled = this["showTitleEnabled"]?.toBooleanStrictOrNull(),
        urlBarHidingEnabled = this["urlBarHidingEnabled"]?.toBooleanStrictOrNull(),
        bookmarksButtonEnabled = this["bookmarksButtonEnabled"]?.toBooleanStrictOrNull(),
        downloadsButtonEnabled = this["downloadsButtonEnabled"]?.toBooleanStrictOrNull(),
        colorScheme = this["colorScheme"]
            ?.let { name -> BrowserCustomization.ColorScheme.entries.find { it.name == name } },
    )
