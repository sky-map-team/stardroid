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

class LocaleSpecTest {
    @Test
    fun `regional tag falls back through language and English to universal`() {
        assertThat(LocaleSpec("pt-BR").fallbackChain)
            .containsExactly("pt-br", "pt", "en", "")
            .inOrder()
    }

    @Test
    fun `multi-segment tag falls back through each parent segment`() {
        assertThat(LocaleSpec("zh-Hans-CN").fallbackChain)
            .containsExactly("zh-hans-cn", "zh-hans", "zh", "en", "")
            .inOrder()
    }

    @Test
    fun `device locales reach the script-subtag codes v1 ships`() {
        // v1 res/ has values-b+zh+Hans and values-b+zh+Hant; catalog codes derived from them
        // ("zh-hans", "zh-hant") must be reachable from the script+region tags devices report.
        assertThat(LocaleSpec("zh-Hant-TW").fallbackChain).contains("zh-hant")
        assertThat(LocaleSpec("zh-Hans-SG").fallbackChain).contains("zh-hans")
    }

    @Test
    fun `region-only Chinese tags infer the script devices leave out`() {
        // Many devices report "zh-TW"/"zh-CN" with no script subtag, unlike the "zh-Hant-TW"
        // case the test above covers. Android's own resource system infers the script from
        // the region here; the catalog fallback chain must too, or catalog-sourced names (as
        // opposed to string-resource ones) silently fall back to English. See issue #1039.
        assertThat(LocaleSpec("zh-TW").fallbackChain)
            .containsExactly("zh-tw", "zh-hant", "zh", "en", "")
            .inOrder()
        assertThat(LocaleSpec("zh-HK").fallbackChain)
            .containsExactly("zh-hk", "zh-hant", "zh", "en", "")
            .inOrder()
        assertThat(LocaleSpec("zh-CN").fallbackChain)
            .containsExactly("zh-cn", "zh-hans", "zh", "en", "")
            .inOrder()
    }

    @Test
    fun `bare Chinese tag defaults to the Simplified script`() {
        // CLDR's likely-subtags default for unqualified "zh" is "zh-Hans-CN".
        assertThat(LocaleSpec("zh").fallbackChain)
            .containsExactly("zh-hans", "zh", "en", "")
            .inOrder()
    }

    @Test
    fun `Chinese tags that already name a script are left alone`() {
        assertThat(LocaleSpec("zh-Hant-TW").fallbackChain)
            .containsExactly("zh-hant-tw", "zh-hant", "zh", "en", "")
            .inOrder()
    }

    @Test
    fun `bare language tag has no duplicate entry`() {
        assertThat(LocaleSpec("de").fallbackChain).containsExactly("de", "en", "").inOrder()
    }

    @Test
    fun `english request collapses to english then universal`() {
        assertThat(LocaleSpec.ENGLISH.fallbackChain).containsExactly("en", "").inOrder()
        assertThat(LocaleSpec("en-GB").fallbackChain)
            .containsExactly("en-gb", "en", "")
            .inOrder()
    }

    @Test
    fun `blank tag means english plus universal`() {
        assertThat(LocaleSpec("").fallbackChain).containsExactly("en", "").inOrder()
    }

    @Test
    fun `tags normalize case and underscore separators`() {
        assertThat(LocaleSpec("PT_br").tag).isEqualTo("pt-br")
        assertThat(LocaleSpec("PT_br").fallbackChain)
            .containsExactly("pt-br", "pt", "en", "")
            .inOrder()
    }

    @Test
    fun `equality and hashCode follow the normalized tag`() {
        assertThat(LocaleSpec("pt-BR")).isEqualTo(LocaleSpec("pt_br"))
        assertThat(LocaleSpec("pt-BR").hashCode()).isEqualTo(LocaleSpec("pt_br").hashCode())
        assertThat(LocaleSpec("pt-BR")).isNotEqualTo(LocaleSpec("en"))
    }
}
