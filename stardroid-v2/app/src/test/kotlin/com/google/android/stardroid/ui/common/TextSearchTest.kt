/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TextSearchTest {
    @Test
    fun `match ignores case`() {
        assertThat(matchesQuery("Calibrate your Compass", "compass")).isTrue()
        assertThat(matchesQuery("calibrate your compass", "COMPASS")).isTrue()
    }

    @Test
    fun `match ignores diacritics in either direction`() {
        assertThat(matchesQuery("Une étoile filante", "etoile")).isTrue()
        assertThat(matchesQuery("Une etoile filante", "étoile")).isTrue()
        assertThat(matchesQuery("Kalibrierung für den Kompass", "fur")).isTrue()
    }

    @Test
    fun `every term must appear, in any order`() {
        assertThat(matchesQuery("The moon phase widget", "moon widget")).isTrue()
        assertThat(matchesQuery("The moon phase widget", "widget moon")).isTrue()
        assertThat(matchesQuery("The moon phase widget", "moon telescope")).isFalse()
    }

    @Test
    fun `blank query matches everything`() {
        assertThat(matchesQuery("Anything at all", "")).isTrue()
        assertThat(matchesQuery("Anything at all", "   ")).isTrue()
    }

    @Test
    fun `no match when the term is absent`() {
        assertThat(matchesQuery("Time travel", "eclipse")).isFalse()
    }

    @Test
    fun `text without word spacing still matches a substring`() {
        // Japanese has no spaces to tokenize on, so the whole query is one term.
        assertThat(matchesQuery("星図を表示します", "星図")).isTrue()
        assertThat(matchesQuery("星図を表示します", "彗星")).isFalse()
    }

    @Test
    fun `ranges are in source offsets, not folded ones`() {
        // "é" folds to two characters ("e" + combining acute) before the mark is dropped, so a
        // naive fold would report the match one character late.
        val text = "Précession of the compass"
        val ranges = FoldedText.of(text).matchRanges("compass")

        assertThat(ranges).hasSize(1)
        assertThat(text.substring(ranges[0].first, ranges[0].last + 1)).isEqualTo("compass")
    }

    @Test
    fun `an accented match covers the accented source characters`() {
        val text = "Une étoile"
        val ranges = FoldedText.of(text).matchRanges("etoile")

        assertThat(ranges).hasSize(1)
        assertThat(text.substring(ranges[0].first, ranges[0].last + 1)).isEqualTo("étoile")
    }

    @Test
    fun `every occurrence of every term is reported, in order`() {
        val text = "Compass and compass and accelerometer"
        val ranges = FoldedText.of(text).matchRanges("compass accelerometer")

        assertThat(ranges.map { text.substring(it.first, it.last + 1) })
            .containsExactly("Compass", "compass", "accelerometer")
            .inOrder()
    }

    @Test
    fun `overlapping and adjacent matches are merged into one range`() {
        val text = "aaaa"
        val ranges = FoldedText.of(text).matchRanges("aa")

        assertThat(ranges).containsExactly(0..3)
    }

    @Test
    fun `blank query highlights nothing`() {
        assertThat(FoldedText.of("Anything").matchRanges("")).isEmpty()
    }
}
