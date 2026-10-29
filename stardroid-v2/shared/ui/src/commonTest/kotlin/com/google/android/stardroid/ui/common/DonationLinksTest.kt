/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class DonationLinksTest {
    @Test
    fun `buy me a coffee links keep their text and lose the anchor`() {
        val link = """<a href="https://buymeacoffee.com/skymapdevs?a=1&amp;b=2">donated!</a>"""
        val html = "<h3>Thanks to these folks who $link</h3>"
        assertEquals("<h3>Thanks to these folks who donated!</h3>", stripDonationLinks(html))
    }

    @Test
    fun `other links are untouched`() {
        val html = """<a href="https://github.com/sky-map-team/stardroid">GitHub</a>"""
        assertEquals(html, stripDonationLinks(html))
    }
}
