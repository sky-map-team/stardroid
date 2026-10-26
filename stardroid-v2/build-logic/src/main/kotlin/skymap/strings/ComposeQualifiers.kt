/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package skymap.strings

/**
 * The Compose Multiplatform folder(s) for an Android resource folder. Compose reads a language
 * and an `r` region, as Android does, but not the BCP 47 `b+` form until 1.12, and tm writes
 * three locales that way. Delete this mapping once the build is on Compose Multiplatform 1.12.
 *
 * - `b+en+GB` (a language and a region) is simply `en-rGB`.
 * - Compose has no script qualifier, so Chinese is told apart by region, as it chooses a folder
 *   for the device's region first and its language second. Simplified is plain `zh`, which
 *   mainland China and Singapore fall back to; Traditional is copied to the Taiwan, Hong Kong
 *   and Macau regions. A Traditional reader set to any other region gets Simplified text until
 *   then.
 */
object ComposeQualifiers {
    fun foldersFor(androidFolder: String): List<String> {
        val prefix = androidFolder.substringBefore("-b+", missingDelimiterValue = "")
        if (prefix.isEmpty()) return listOf(androidFolder)
        val tag = androidFolder.substringAfter("-b+").split('+')
        return when {
            tag == listOf("zh", "Hans") -> listOf("$prefix-zh")
            tag == listOf("zh", "Hant") -> TRADITIONAL_REGIONS.map { "$prefix-zh-r$it" }
            tag.size == 2 && tag[1].matches(Regex("[A-Z]{2}")) -> listOf("$prefix-${tag[0]}-r${tag[1]}")
            else -> error("$androidFolder: no Compose Multiplatform equivalent before 1.12")
        }
    }

    private val TRADITIONAL_REGIONS = listOf("TW", "HK", "MO")
}
