/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.compat

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The backports must match the JDK methods they replace exactly — checked against the JDK. */
class RoboVmBackportsTest {
    private val longs =
        // Around the edges the JDK's overflow checks care about (3_037_000_499² < 2⁶³ < …500²).
        listOf(0L, 1L, -1L, -7L, 3_037_000_499L, 3_037_000_500L, Int.MAX_VALUE.toLong()) +
            listOf(Long.MAX_VALUE, Long.MIN_VALUE)

    @Test
    fun booleanHashCode_matchesTheJdk() {
        for (b in listOf(true, false)) {
            assertThat(RoboVmBackports.boolean_hashCode(b)).isEqualTo(java.lang.Boolean.hashCode(b))
        }
    }

    @Test
    fun addExact_matchesTheJdk() = forAllPairs(Math::addExact, RoboVmBackports::math_addExact)

    @Test
    fun multiplyExact_matchesTheJdk() =
        forAllPairs(
            Math::multiplyExact,
            RoboVmBackports::math_multiplyExact,
        )

    @Test
    fun overflow_throwsArithmeticException() {
        assertThrows<ArithmeticException> { RoboVmBackports.math_addExact(Long.MAX_VALUE, 1) }
        assertThrows<ArithmeticException> { RoboVmBackports.math_multiplyExact(Long.MIN_VALUE, -1) }
    }

    private fun forAllPairs(
        jdk: (Long, Long) -> Long,
        backport: (Long, Long) -> Long,
    ) {
        for (x in longs) {
            for (y in longs) {
                val expected = runCatching { jdk(x, y) }
                val actual = runCatching { backport(x, y) }
                assertThat(actual.getOrNull()).isEqualTo(expected.getOrNull())
                assertThat(actual.isFailure).isEqualTo(expected.isFailure)
            }
        }
    }
}
