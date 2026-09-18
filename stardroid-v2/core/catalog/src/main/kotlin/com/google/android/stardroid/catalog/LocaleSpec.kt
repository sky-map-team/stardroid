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
     *
     * Chinese catalog data is split by script (`zh-hans` / `zh-hant`), not by region, but many
     * devices report a region-only tag with no script subtag — `zh-TW`, `zh-CN` — where Android's
     * own resource system infers the script from the region (via ICU's likely-subtags) but a
     * plain right-to-left strip never would. So a `zh` tag without an explicit script gets the
     * region's implied script (`tw`/`hk`/`mo` → `zh-hant`, anything else, including no region →
     * `zh-hans`, CLDR's default) inserted just ahead of the bare `"zh"` fallback: `"zh-TW"` →
     * `["zh-tw", "zh-hant", "zh", "en", ""]`.
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
        }.withImpliedChineseScript(tag).distinct()

    override fun equals(other: Any?): Boolean = other is LocaleSpec && tag == other.tag

    override fun hashCode(): Int = tag.hashCode()

    override fun toString(): String = "LocaleSpec(tag=$tag)"

    companion object {
        val ENGLISH = LocaleSpec("en")

        /** Regions whose implied Han script is Traditional rather than the CLDR default. */
        private val TRADITIONAL_REGIONS = setOf("tw", "hk", "mo")

        /**
         * The `zh-hant`/`zh-hans` [tag] implies, or `null` if it isn't Chinese or already names
         * a script.
         */
        private fun impliedChineseScript(tag: String): String? {
            if (tag != "zh" && !tag.startsWith("zh-")) return null
            val subtags = tag.split('-')
            if (subtags.any { it == "hans" || it == "hant" }) return null
            return if (subtags.any { it in TRADITIONAL_REGIONS }) "zh-hant" else "zh-hans"
        }

        /** Inserts the script [tag] implies just ahead of the bare `"zh"` entry, if present. */
        private fun List<String>.withImpliedChineseScript(tag: String): List<String> {
            val zhIndex = indexOf("zh")
            val script = if (zhIndex >= 0) impliedChineseScript(tag) else null
            return if (script == null) this else toMutableList().apply { add(zhIndex, script) }
        }
    }
}
