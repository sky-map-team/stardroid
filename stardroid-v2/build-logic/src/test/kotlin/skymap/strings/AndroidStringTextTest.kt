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

/** Each case is what aapt2 compiles the same text to (checked against `aapt2 dump resources`). */
class AndroidStringTextTest {
    @Test
    fun `plain text is unchanged`() {
        assertThat(AndroidStringText.process("No Thanks")).isEqualTo("No Thanks")
    }

    @Test
    fun `escaped apostrophes and quotes lose their backslash`() {
        assertThat(AndroidStringText.process("""We don\'t say \"as is\""""))
            .isEqualTo("""We don't say "as is"""")
    }

    @Test
    fun `whitespace runs collapse and the ends are trimmed`() {
        assertThat(AndroidStringText.process("\n    <br/>\n    <p>Hi</p>\n    "))
            .isEqualTo("<br/> <p>Hi</p>")
    }

    @Test
    fun `a string with an unescaped quote keeps its edge spaces`() {
        assertThat(AndroidStringText.process("\n  <a href=\"x\">y</a>\n  "))
            .isEqualTo(" <a href=x>y</a> ")
    }

    @Test
    fun `escaped quotes do not stop the trimming`() {
        assertThat(AndroidStringText.process("\n  say \\\"hi\\\"\n  ")).isEqualTo("say \"hi\"")
    }

    @Test
    fun `escaped newlines and tabs are kept and do not collapse`() {
        assertThat(AndroidStringText.process("""a\n  b\tc""")).isEqualTo("a\n b\tc")
    }

    @Test
    fun `unicode escapes decode`() {
        assertThat(AndroidStringText.process("""été""")).isEqualTo("été")
    }

    @Test
    fun `double quotes preserve whitespace and are dropped`() {
        assertThat(AndroidStringText.process("\"  two  spaces \"")).isEqualTo("  two  spaces ")
    }

    @Test
    fun `an apostrophe inside double quotes needs no escape`() {
        assertThat(AndroidStringText.process("\"don't\"")).isEqualTo("don't")
    }

    @Test
    fun `other escapes keep the character`() {
        assertThat(AndroidStringText.process("""\@home \?attr \#tag \\ \x"""))
            .isEqualTo("@home ?attr #tag \\ x")
    }

    @Test
    fun `a no-break space is text`() {
        assertThat(AndroidStringText.process("Non\u00A0!")).isEqualTo("Non\u00A0!")
    }

    @Test
    fun `an unescaped apostrophe is an error`() {
        assertThrows<IllegalArgumentException> { AndroidStringText.process("don't") }
    }
}
