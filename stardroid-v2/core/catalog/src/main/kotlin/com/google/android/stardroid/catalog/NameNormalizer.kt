/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.catalog

import java.text.Normalizer

/**
 * The single definition of "normalized name" (`object_name.name_normalized`): diacritics
 * stripped, every run of characters other than letters, marks and digits collapsed to one
 * space, lowercased. The result is exactly the words the `simple` FTS tokenizer indexes, which
 * is what keeps search accent-insensitive. The `:data` pack writer applies it on insert, search
 * applies it to queries, and the `:data:generator` build-time DB generator applies it at
 * generation — living here lets all three share one implementation (D33).
 */
object NameNormalizer {
    private val combiningMarks = Regex("\\p{Mn}+")

    // Marks stay word characters: a spacing vowel sign (e.g. Devanagari) must not split a word.
    private val separatorRuns = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(separatorRuns, " ")
            .trim()
            .lowercase()
}
