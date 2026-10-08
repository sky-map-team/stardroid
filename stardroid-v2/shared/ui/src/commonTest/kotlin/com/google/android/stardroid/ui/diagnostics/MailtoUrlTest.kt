/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals

class MailtoUrlTest {
    @Test
    fun `addresses the mail and encodes the separators a report contains`() {
        assertEquals(
            "mailto:dev%40example.com?subject=Sky%20Map&body=a%3D1%26b%0Ac",
            mailtoUrl("dev@example.com", "Sky Map", "a=1&b\nc"),
        )
    }

    @Test
    fun `leaves only unreserved characters bare`() {
        assertEquals("AZaz09-._~", percentEncode("AZaz09-._~"))
        assertEquals("%2B%3F%23%25%2F", percentEncode("+?#%/"))
    }

    @Test
    fun `encodes non-ASCII text as its UTF-8 bytes`() {
        // A degree sign and a sigma, as the jitter row writes them.
        assertEquals("%C2%B0%CF%83", percentEncode("°σ"))
    }
}
