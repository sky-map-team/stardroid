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

class ComposeQualifiersTest {
    @Test
    fun `plain folders carry over`() {
        assertThat(ComposeQualifiers.foldersFor("values")).containsExactly("values")
        assertThat(ComposeQualifiers.foldersFor("values-fr")).containsExactly("values-fr")
        assertThat(ComposeQualifiers.foldersFor("values-pt-rBR")).containsExactly("values-pt-rBR")
    }

    @Test
    fun `a language and region tag becomes an r region`() {
        assertThat(ComposeQualifiers.foldersFor("values-b+en+GB")).containsExactly("values-en-rGB")
    }

    @Test
    fun `simplified chinese is the zh default`() {
        assertThat(ComposeQualifiers.foldersFor("values-b+zh+Hans")).containsExactly("values-zh")
    }

    @Test
    fun `traditional chinese covers its regions`() {
        assertThat(ComposeQualifiers.foldersFor("values-b+zh+Hant"))
            .containsExactly("values-zh-rTW", "values-zh-rHK", "values-zh-rMO")
    }

    @Test
    fun `other scripts fail the build`() {
        assertThrows<IllegalStateException> { ComposeQualifiers.foldersFor("values-b+sr+Latn") }
    }
}
