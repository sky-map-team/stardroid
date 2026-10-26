/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.help

import com.google.android.stardroid.ui.common.isAppLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelpLinksTest {
    @Test
    fun `each target parses to its destination`() {
        assertEquals(HelpLink.Widgets, parseHelpLink("skymap://widgets"))
        assertEquals(HelpLink.Destination.SETTINGS, parseHelpLink("skymap://settings"))
        assertEquals(HelpLink.Destination.DIAGNOSTICS, parseHelpLink("skymap://diagnostics"))
        assertEquals(HelpLink.Destination.CALIBRATE, parseHelpLink("skymap://calibrate"))
        assertEquals(HelpLink.Destination.GALLERY, parseHelpLink("skymap://gallery"))
        assertEquals(HelpLink.Destination.TUTORIAL, parseHelpLink("skymap://tutorial"))
        assertEquals(HelpLink.Destination.APP_SETTINGS, parseHelpLink("skymap://app-settings"))
    }

    @Test
    fun `an anchor naming a real section parses`() {
        assertEquals(
            HelpLink.Anchor("troubleshooting"),
            parseHelpLink("skymap://help#troubleshooting"),
        )
    }

    @Test
    fun `unrecognised links are inert rather than fatal`() {
        // These arrive from translated resources, so a mangled href must not throw.
        assertNull(parseHelpLink("skymap://help#no-such-section"))
        assertNull(parseHelpLink("skymap://teleport"))
        assertNull(parseHelpLink("skymap://"))
        assertNull(parseHelpLink("https://stardroid.app"))
        assertNull(parseHelpLink("mailto:skymapdevs@gmail.com"))
    }

    @Test
    fun `only our own scheme is routed internally`() {
        // The fork that keeps the document's real links reaching the platform URI handler
        // once a link listener is installed.
        assertTrue(isAppLink("skymap://settings"))
        assertFalse(isAppLink("https://stardroid.app"))
        assertFalse(isAppLink("https://ts.stardroid.app"))
        assertFalse(isAppLink("mailto:skymapdevs@gmail.com"))
    }

    @Test
    fun `anchors are unique so a link can only mean one section`() {
        assertEquals(HELP_DOCUMENT.size, HELP_ANCHORS.size)
    }
}
