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
 * A locale request with its name-resolution fallback chain: exact tag → bare language → English
 * → the universal locale `""` (used for designations like "M31" or "NGC 224", which match in
 * every locale). Locale codes in catalog data are lowercase BCP 47 language, optionally with a
 * region ("pt", "pt-br"); [tag] is normalized to that form.
 */
class LocaleSpec(requestedTag: String) {
    /** The normalized request tag, e.g. `"pt-br"`. */
    val tag: String = requestedTag.trim().replace('_', '-').lowercase()

    /**
     * Locale codes to match catalog rows against, most specific first, always ending with
     * English and the universal locale. Subtags are stripped from the right one at a time, so
     * multi-segment tags visit every parent: `"pt-BR"` → `["pt-br", "pt", "en", ""]`,
     * `"zh-Hans-CN"` → `["zh-hans-cn", "zh-hans", "zh", "en", ""]`.
     */
    val fallbackChain: List<String> =
        buildList {
            var current = tag
            while (current.isNotEmpty()) {
                add(current)
                current = current.substringBeforeLast('-', missingDelimiterValue = "")
            }
            add("en")
            add("")
        }.distinct()

    override fun equals(other: Any?): Boolean = other is LocaleSpec && tag == other.tag

    override fun hashCode(): Int = tag.hashCode()

    override fun toString(): String = "LocaleSpec(tag=$tag)"

    companion object {
        val ENGLISH = LocaleSpec("en")
    }
}
