/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.text

import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class TextTemplateTest {
    private fun fill(
        template: String,
        vararg args: Any,
    ) = TextTemplate.fill(template, args)

    @Test
    fun `strings and integers fill by position`() {
        assertThat(fill("%2\$s · %1\$s", "a", "b")).isEqualTo("b · a")
        assertThat(fill("%1\$d days", 3)).isEqualTo("3 days")
    }

    @Test
    fun `a doubled percent is a percent sign`() {
        assertThat(fill("The %1\$d%%-lit moon", 90)).isEqualTo("The 90%-lit moon")
    }

    @Test
    fun `decimals round to the precision asked for`() {
        assertThat(fill("mag %1\$.1f", -3.46)).isEqualTo("mag -3.5")
        assertThat(fill("mag %1\$.1f", 2.0)).isEqualTo("mag 2.0")
        assertThat(fill("%1\$.2f", 0.005)).isEqualTo("0.01")
    }

    @Test
    fun `a plus flag signs positive numbers`() {
        assertThat(fill("%1\$+.1f", 4.25)).isEqualTo("+4.3")
        assertThat(fill("%1\$+.1f", -4.25)).isEqualTo("-4.3")
    }

    @Test
    fun `zero padding keeps the sign in front`() {
        assertThat(fill("%1\$02d:%2\$02d", 7, 5)).isEqualTo("07:05")
        assertThat(fill("%1\$03d", -4)).isEqualTo("-04")
    }

    @Test
    fun `a missing argument is an error not a raw placeholder`() {
        val failed = runCatching { fill("%2\$s", "only one") }.isFailure

        assertThat(failed).isTrue()
    }
}
