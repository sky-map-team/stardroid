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
 * The pieces of turning Android-format strings into an Apple `Localizable.strings`: the `.lproj`
 * for a resource folder, Android's format specifiers in Foundation's form, and the file's own
 * escaping. The text itself comes through [AndroidStringText] first, as for Compose.
 */
object AppleStrings {
    /**
     * The `.lproj` name for an Android `values*` folder, or null for a folder that isn't a locale
     * (`values-night`, `values-v31`). The default folder is English, the development language.
     */
    fun lprojFor(androidFolder: String): String? {
        if (androidFolder == "values") return "en"
        val qualifier = androidFolder.removePrefix("values-")
        if (qualifier == androidFolder) return null
        if (qualifier.startsWith("b+")) {
            val tag = qualifier.removePrefix("b+").split('+')
            return (listOf(language(tag[0]) ?: return null) + tag.drop(1)).joinToString("-")
        }
        val parts = qualifier.split('-')
        val language = language(parts[0]) ?: return null
        return when {
            parts.size == 1 -> language
            parts.size == 2 && parts[1].matches(Regex("r[A-Z]{2}")) ->
                "$language-${parts[1].drop(1)}"
            else -> null
        }
    }

    // Android still accepts the withdrawn ISO 639 codes; Apple uses the current ones.
    private fun language(code: String): String? =
        when {
            !code.matches(Regex("[a-z]{2,3}")) -> null
            code == "iw" -> "he"
            code == "in" -> "id"
            code == "ji" -> "yi"
            else -> code
        }

    private val specifier = Regex("""%(%|(\d+\$)?[-+ 0#]*\d*(\.\d+)?[a-zA-Z])""")

    /** Android's `%s` is a Java string; Foundation's is `%@`. Everything else carries over. */
    fun format(text: String): String =
        specifier.replace(text) { match ->
            val spec = match.value
            if (spec.endsWith("s")) spec.dropLast(1) + "@" else spec
        }

    fun escape(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\t", "\\t")

    /** A `Localizable.strings` file, keys in the order given. */
    fun file(strings: Map<String, String>): String =
        strings.entries.joinToString("\n", postfix = "\n") { (key, value) ->
            "\"${escape(key)}\" = \"${escape(format(value))}\";"
        }
}
