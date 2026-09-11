/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NameNormalizerTest {
    @Test
    fun `strips diacritics and lowercases`() {
        assertThat(NameNormalizer.normalize("Galaxia de Andrómeda"))
            .isEqualTo("galaxia de andromeda")
        assertThat(NameNormalizer.normalize("Étoile Polaire")).isEqualTo("etoile polaire")
        assertThat(NameNormalizer.normalize("İstanbul")).isEqualTo("istanbul")
    }

    @Test
    fun `folds every non-alphanumeric run to one space`() {
        assertThat(NameNormalizer.normalize("Barnard’s Star")).isEqualTo("barnard s star")
        assertThat(NameNormalizer.normalize("M 31")).isEqualTo("m 31")
        assertThat(NameNormalizer.normalize("Omega Centauri – NGC 5139"))
            .isEqualTo("omega centauri ngc 5139")
        assertThat(NameNormalizer.normalize("  Pleiades (M45)  ")).isEqualTo("pleiades m45")
    }

    @Test
    fun `keeps spacing marks inside a word`() {
        // Devanagari "prithvi": the vowel signs and virama that are Mn are dropped, but the
        // spacing vowel sign (Mc) stays, so the word is not split.
        assertThat(NameNormalizer.normalize("पृथ्वी"))
            .isEqualTo("पथवी")
    }
}
