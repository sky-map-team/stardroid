/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import java.text.Normalizer

/**
 * A searchable projection of some prose: lower-cased and stripped of diacritics, keeping a map
 * back to the offsets in the text it came from so a match can still be highlighted in place.
 *
 * The fold matters because the help document is translated into 32 locales: a French reader who
 * types "etoile" expects to find "étoile", and one who types "Étoile" expects the same. Folding
 * changes the length of the string — "é" folds to one character, "ﬁ" would not fold at all — so
 * the offsets cannot simply be reused, hence [origin].
 */
internal class FoldedText private constructor(
    val text: String,
    /** For each character of [text], the index in the source string it came from. */
    private val origin: IntArray,
) {
    /** Maps a half-open range of [text] back to an inclusive range of the source string. */
    fun sourceRange(
        start: Int,
        endExclusive: Int,
    ): IntRange = origin[start]..origin[endExclusive - 1]

    companion object {
        fun of(source: String): FoldedText {
            val folded = StringBuilder(source.length)
            val origin = IntArray(source.length)
            var length = 0
            for (index in source.indices) {
                val char = source[index]
                // ASCII can't carry a combining mark, and normalizing it is pure overhead on a
                // document this size.
                val decomposed =
                    if (char.code < 0x80) {
                        char.toString()
                    } else {
                        Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
                    }
                for (decomposedChar in decomposed) {
                    if (decomposedChar.isCombiningMark()) continue
                    if (length == origin.size) {
                        // A character that folds to more than one (e.g. "ﬄ") would overrun the
                        // source-length estimate; such characters are vanishingly rare, so grow
                        // rather than size for the worst case up front.
                        return ofSlow(source)
                    }
                    folded.append(decomposedChar.lowercaseChar())
                    origin[length++] = index
                }
            }
            return FoldedText(folded.toString(), origin.copyOf(length))
        }

        /** The general path, for the rare text whose fold is longer than the original. */
        private fun ofSlow(source: String): FoldedText {
            val folded = StringBuilder(source.length)
            val origin = ArrayList<Int>(source.length)
            for (index in source.indices) {
                val decomposed = Normalizer.normalize(source[index].toString(), Normalizer.Form.NFD)
                for (decomposedChar in decomposed) {
                    if (decomposedChar.isCombiningMark()) continue
                    folded.append(decomposedChar.lowercaseChar())
                    origin.add(index)
                }
            }
            return FoldedText(folded.toString(), origin.toIntArray())
        }

        private fun Char.isCombiningMark(): Boolean =
            when (category) {
                CharCategory.NON_SPACING_MARK,
                CharCategory.COMBINING_SPACING_MARK,
                CharCategory.ENCLOSING_MARK,
                -> true
                else -> false
            }
    }
}

/**
 * Splits a query into the terms every match must contain. Whitespace-separated, so "moon
 * widget" finds the paragraph mentioning both in either order; a language without word spacing
 * simply yields one term, which plain substring matching handles correctly.
 */
internal fun searchTerms(query: String): List<String> =
    FoldedText.of(query).text.split(' ', '\t', '\n').filter { it.isNotEmpty() }

/** True when every term of [query] appears somewhere in the folded text. A blank query matches. */
internal fun FoldedText.matches(query: String): Boolean =
    searchTerms(query).all { text.contains(it) }

/**
 * Every occurrence of every term of [query], as inclusive ranges of the *source* string, in
 * ascending order. Overlapping ranges are merged so a highlight span is never applied twice.
 */
internal fun FoldedText.matchRanges(query: String): List<IntRange> {
    val terms = searchTerms(query)
    if (terms.isEmpty()) return emptyList()
    val found = mutableListOf<IntRange>()
    for (term in terms) {
        var from = text.indexOf(term)
        while (from >= 0) {
            found.add(sourceRange(from, from + term.length))
            from = text.indexOf(term, from + 1)
        }
    }
    found.sortBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (range in found) {
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged.add(range)
        }
    }
    return merged
}

/** Convenience for callers with no folded text to reuse. */
internal fun matchesQuery(
    text: String,
    query: String,
): Boolean = FoldedText.of(text).matches(query)
