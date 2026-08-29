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
 * The single definition of "normalized name" (`object_name.name_normalized`): lowercased,
 * diacritics stripped, whitespace collapsed. The `:data` pack writer applies it on insert,
 * search applies it to queries, and the `:data:generator` build-time DB generator applies it
 * at generation — living here lets all three share one implementation (D33).
 */
object NameNormalizer {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespaceRuns = Regex("\\s+")

    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(whitespaceRuns, " ")
            .trim()
            .lowercase()
}
