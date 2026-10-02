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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextSearchTest {
    @Test
    fun `match ignores case`() {
        assertTrue(matchesQuery("Calibrate your Compass", "compass"))
        assertTrue(matchesQuery("calibrate your compass", "COMPASS"))
    }

    @Test
    fun `match ignores diacritics in either direction`() {
        assertTrue(matchesQuery("Une étoile filante", "etoile"))
        assertTrue(matchesQuery("Une etoile filante", "étoile"))
        assertTrue(matchesQuery("Kalibrierung für den Kompass", "fur"))
    }

    @Test
    fun `every term must appear - in any order`() {
        assertTrue(matchesQuery("The moon phase widget", "moon widget"))
        assertTrue(matchesQuery("The moon phase widget", "widget moon"))
        assertFalse(matchesQuery("The moon phase widget", "moon telescope"))
    }

    @Test
    fun `blank query matches everything`() {
        assertTrue(matchesQuery("Anything at all", ""))
        assertTrue(matchesQuery("Anything at all", "   "))
    }

    @Test
    fun `no match when the term is absent`() {
        assertFalse(matchesQuery("Time travel", "eclipse"))
    }

    @Test
    fun `text without word spacing still matches a substring`() {
        // Japanese has no spaces to tokenize on, so the whole query is one term.
        assertTrue(matchesQuery("星図を表示します", "星図"))
        assertFalse(matchesQuery("星図を表示します", "彗星"))
    }

    @Test
    fun `ranges are in source offsets - not folded ones`() {
        // "é" folds to two characters ("e" + combining acute) before the mark is dropped, so a
        // naive fold would report the match one character late.
        val text = "Précession of the compass"
        val ranges = FoldedText.of(text).matchRanges("compass")

        assertEquals(1, ranges.size)
        assertEquals("compass", text.substring(ranges[0].first, ranges[0].last + 1))
    }

    @Test
    fun `an accented match covers the accented source characters`() {
        val text = "Une étoile"
        val ranges = FoldedText.of(text).matchRanges("etoile")

        assertEquals(1, ranges.size)
        assertEquals("étoile", text.substring(ranges[0].first, ranges[0].last + 1))
    }

    @Test
    fun `every occurrence of every term is reported - in order`() {
        val text = "Compass and compass and accelerometer"
        val ranges = FoldedText.of(text).matchRanges("compass accelerometer")

        assertEquals(
            listOf("Compass", "compass", "accelerometer"),
            ranges.map { text.substring(it.first, it.last + 1) },
        )
    }

    @Test
    fun `overlapping and adjacent matches are merged into one range`() {
        val text = "aaaa"
        val ranges = FoldedText.of(text).matchRanges("aa")

        assertEquals(listOf(0..3), ranges)
    }

    @Test
    fun `blank query highlights nothing`() {
        assertTrue(FoldedText.of("Anything").matchRanges("").isEmpty())
    }
}
