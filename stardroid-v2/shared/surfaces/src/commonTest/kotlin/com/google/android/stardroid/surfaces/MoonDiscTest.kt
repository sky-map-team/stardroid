/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class MoonDiscTest {
    @Test
    fun `a waxing northern moon is drawn as is`() {
        assertThat(moonDisc(0.3, waxing = true, mirrored = false).flipped).isFalse()
    }

    @Test
    fun `waning or southern flips the disc and both flip it back`() {
        assertThat(moonDisc(0.3, waxing = false, mirrored = false).flipped).isTrue()
        assertThat(moonDisc(0.3, waxing = true, mirrored = true).flipped).isTrue()
        assertThat(moonDisc(0.3, waxing = false, mirrored = true).flipped).isFalse()
    }

    @Test
    fun `half full has a straight terminator`() {
        assertThat(moonDisc(0.5, true, false).terminatorRx).isWithin(1e-12).of(0.0)
    }

    @Test
    fun `new and full have a terminator on the limb`() {
        assertThat(moonDisc(0.0, true, false).terminatorRx).isWithin(1e-12).of(1.0)
        assertThat(moonDisc(1.0, true, false).terminatorRx).isWithin(1e-12).of(1.0)
    }

    @Test
    fun `a gibbous terminator bulges into the dark half and a crescent's does not`() {
        val gibbous = moonDisc(0.8, true, false)
        val crescent = moonDisc(0.2, true, false)
        assertThat(gibbous.terminatorBulgesLeft).isTrue()
        assertThat(crescent.terminatorBulgesLeft).isFalse()
        assertThat(gibbous.terminatorRx).isWithin(1e-12).of(0.6)
        assertThat(crescent.terminatorRx).isWithin(1e-12).of(0.6)
    }

    @Test
    fun `an out of range fraction is clamped`() {
        assertThat(moonDisc(1.2, true, false)).isEqualTo(moonDisc(1.0, true, false))
        assertThat(moonDisc(-0.1, true, false)).isEqualTo(moonDisc(0.0, true, false))
    }
}
