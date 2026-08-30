/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.share

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * The share downsample policy (audit-2026-08 M5).
 *
 * This decides the resolution of every image the app shares, and it is the lever that keeps
 * the compositing pipeline's four-to-six simultaneous `ARGB_8888` bitmaps out of
 * `OutOfMemoryError` territory — so both halves are worth pinning: that it shrinks what is
 * too big, and that it leaves alone what is not.
 */
class ShareScaledSizeTest {
    @Test
    fun `a tall phone capture is capped on its long edge`() {
        val size = shareScaledSize(1440, 3120, MAX_EDGE)

        assertThat(size).isNotNull()
        assertThat(size!!.height).isEqualTo(MAX_EDGE)
        assertThat(size.width).isEqualTo(945)
    }

    @Test
    fun `a wide capture is capped on its width instead`() {
        val size = shareScaledSize(4000, 3000, MAX_EDGE)

        assertThat(size!!.width).isEqualTo(MAX_EDGE)
        assertThat(size.height).isEqualTo(1536)
    }

    @Test
    fun `aspect ratio survives the scale`() {
        val size = shareScaledSize(1440, 3120, MAX_EDGE)!!

        val before = 1440.0 / 3120.0
        val after = size.width.toDouble() / size.height
        // Integer pixels can't hold the ratio exactly; a rounding error's worth is the bound.
        assertThat(abs(after - before)).isLessThan(0.001)
    }

    @Test
    fun `a capture already within the cap is left alone`() {
        // Null, not an identity size — the caller uses this to skip the copy entirely and
        // keep the bitmap it already has.
        assertThat(shareScaledSize(1080, 1920, MAX_EDGE)).isNull()
    }

    @Test
    fun `a capture exactly at the cap is left alone`() {
        assertThat(shareScaledSize(1000, MAX_EDGE, MAX_EDGE)).isNull()
    }

    @Test
    fun `an extreme aspect ratio still yields a drawable size`() {
        // Bitmap.createScaledBitmap throws on a zero dimension, so the short edge must never
        // round away to nothing however lopsided the source.
        val size = shareScaledSize(100_000, 3, MAX_EDGE)!!

        assertThat(size.width).isEqualTo(MAX_EDGE)
        assertThat(size.height).isAtLeast(1)
    }

    private companion object {
        const val MAX_EDGE = 2048
    }
}
