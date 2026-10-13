/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.catalog

/**
 * The single definition of "normalized name" (`object_name.name_normalized`): diacritics
 * stripped, every run of characters other than letters, marks and digits collapsed to one
 * space, lowercased. The result is exactly the words the `simple` FTS tokenizer indexes, which
 * is what keeps search accent-insensitive. The `:data` pack writer applies it on insert, search
 * applies it to queries, and the `:data:generator` build-time DB generator applies it at
 * generation — living here lets all three share one implementation (D33).
 *
 * Only the canonical decomposition is platform code ([decomposeCanonically]). The rest is
 * common Kotlin, but `Regex` and `lowercase` still run on each platform's own Unicode tables —
 * which is why this object's tests have to pass on every target, not just the JVM.
 */
object NameNormalizer {
    private val combiningMarks = Regex("\\p{Mn}+")

    // Marks stay word characters: a spacing vowel sign (e.g. Devanagari) must not split a word.
    // Numbers are spelled out as Nd/Nl/No — exactly the Unicode category N — because
    // Kotlin/Native's regex engine rejects `\p{N}` (though it accepts `\p{L}` and `\p{M}`).
    private val separatorRuns = Regex("[^\\p{L}\\p{M}\\p{Nd}\\p{Nl}\\p{No}]+")

    fun normalize(name: String): String =
        decomposeCanonically(name)
            .replace(combiningMarks, "")
            .replace(separatorRuns, " ")
            .trim()
            .lowercase()
}

/** Unicode canonical decomposition (NFD) — the one platform-specific step of [NameNormalizer]. */
internal expect fun decomposeCanonically(text: String): String
