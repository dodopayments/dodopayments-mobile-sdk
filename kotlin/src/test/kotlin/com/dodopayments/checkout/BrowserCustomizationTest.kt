package com.dodopayments.checkout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserCustomizationTest {

    @Test
    fun defaultsAreAllNullSoNothingIsAssertedOnThePlatformsBehalf() {
        // null means the corresponding CustomTabsIntent.Builder setter is
        // never called at all, so the Custom Tab host's own live default
        // applies — this SDK doesn't hardcode a guess at what that is
        // anywhere. See BrowserCheckoutHostActivity.applyBrowserCustomization
        // for where each null is actually resolved (skipped).
        val defaults = BrowserCustomization()
        assertNull(defaults.toolbarColor)
        assertNull(defaults.navigationBarColor)
        assertNull(defaults.navigationBarDividerColor)
        assertNull(defaults.closeButtonStyle)
        assertNull(defaults.closeButtonPosition)
        assertNull(defaults.shareButtonEnabled)
        assertNull(defaults.showTitleEnabled)
        assertNull(defaults.urlBarHidingEnabled)
        assertNull(defaults.bookmarksButtonEnabled)
        assertNull(defaults.downloadsButtonEnabled)
        assertNull(defaults.colorScheme)
    }

    @Test
    fun emptyMapDecodesToDefaults() {
        assertEquals(BrowserCustomization(), emptyMap<String, String>().toBrowserCustomization())
    }

    @Test
    fun roundTripsThroughStringMap() {
        val customization = BrowserCustomization(
            toolbarColor = -0x123457, // an arbitrary negative ARGB int
            navigationBarColor = 0x11223344,
            navigationBarDividerColor = 0,
            closeButtonStyle = BrowserCustomization.CloseButtonStyle.BACK,
            closeButtonPosition = BrowserCustomization.CloseButtonPosition.END,
            shareButtonEnabled = false,
            showTitleEnabled = true,
            urlBarHidingEnabled = true,
            bookmarksButtonEnabled = false,
            downloadsButtonEnabled = false,
            colorScheme = BrowserCustomization.ColorScheme.DARK,
        )
        assertEquals(customization, customization.toStringMap().toBrowserCustomization())
    }

    @Test
    fun unrecognizedEnumValueDecodesToNullRatherThanAStaleDefault() {
        val decoded = mapOf(
            "closeButtonStyle" to "SOMETHING_FROM_A_NEWER_SDK_VERSION",
            "closeButtonPosition" to "also-unrecognized",
            "colorScheme" to "also-unrecognized"
        ).toBrowserCustomization()
        assertNull(decoded.closeButtonStyle)
        assertNull(decoded.closeButtonPosition)
        assertNull(decoded.colorScheme)
    }

    @Test
    fun malformedColorFallsBackToUnset() {
        val decoded = mapOf("toolbarColor" to "not-a-number").toBrowserCustomization()
        assertEquals(null, decoded.toolbarColor)
    }
}
