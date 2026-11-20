/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package skymap.strings

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppleStringsTest {
    @Test
    fun `the default folder is english`() {
        assertThat(AppleStrings.lprojFor("values")).isEqualTo("en")
    }

    @Test
    fun `languages and regions map to apple locale ids`() {
        assertThat(AppleStrings.lprojFor("values-fr")).isEqualTo("fr")
        assertThat(AppleStrings.lprojFor("values-pt-rBR")).isEqualTo("pt-BR")
        assertThat(AppleStrings.lprojFor("values-b+en+GB")).isEqualTo("en-GB")
        assertThat(AppleStrings.lprojFor("values-b+zh+Hans")).isEqualTo("zh-Hans")
        assertThat(AppleStrings.lprojFor("values-b+zh+Hant")).isEqualTo("zh-Hant")
    }

    @Test
    fun `withdrawn language codes become the current ones`() {
        assertThat(AppleStrings.lprojFor("values-iw")).isEqualTo("he")
        assertThat(AppleStrings.lprojFor("values-in")).isEqualTo("id")
    }

    @Test
    fun `folders that are not locales are skipped`() {
        assertThat(AppleStrings.lprojFor("values-night")).isNull()
        assertThat(AppleStrings.lprojFor("values-v31")).isNull()
        assertThat(AppleStrings.lprojFor("drawable")).isNull()
    }

    @Test
    fun `string specifiers become objects and the rest carry over`() {
        assertThat(AppleStrings.format("rises %1\$s")).isEqualTo("rises %1\$@")
        assertThat(AppleStrings.format("%s and %2\$d%%")).isEqualTo("%@ and %2\$d%%")
        assertThat(AppleStrings.format("%.1f%%s")).isEqualTo("%.1f%%s")
    }

    @Test
    fun `the file escapes quotes, backslashes and newlines`() {
        val file = AppleStrings.file(mapOf("a" to "say \"hi\"\\\nnow", "b" to " +%1\$d"))
        assertThat(file).isEqualTo("\"a\" = \"say \\\"hi\\\"\\\\\\nnow\";\n\"b\" = \" +%1\$d\";\n")
    }

    @Test
    fun `a locale missing a key gets the english text, in the wanted order`() {
        val complete =
            AppleStrings.complete(
                mapOf("en" to mapOf("a" to "A", "b" to "B"), "fr" to mapOf("b" to "Bé")),
                listOf("b", "a"),
            )
        assertThat(complete["fr"]).containsExactly("b", "Bé", "a", "A").inOrder()
        assertThat(complete["en"]).containsExactly("b", "B", "a", "A").inOrder()
    }

    @Test
    fun `a key with no english string fails the export`() {
        assertThrows<IllegalArgumentException> {
            AppleStrings.complete(mapOf("en" to mapOf("a" to "A")), listOf("a", "b"))
        }
    }
}
