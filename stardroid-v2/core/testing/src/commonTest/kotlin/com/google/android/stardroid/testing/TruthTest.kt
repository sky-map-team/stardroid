/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Every assertion must fail when it should. A shim that passes vacuously would turn every suite
 * built on it green without checking anything, so each check here is paired with its failure.
 */
class TruthTest {
    private fun fails(block: () -> Unit) {
        assertFailsWith<AssertionError> { block() }
    }

    @Test
    fun isWithin_passesInsideToleranceAndFailsOutside() {
        assertThat(1.0).isWithin(0.1).of(1.05)
        assertThat(1.0).isWithin(0.0).of(1.0)
        fails { assertThat(1.0).isWithin(0.1).of(1.2) }
        assertThat(1f).isWithin(0.1f).of(1.05f)
        fails { assertThat(1f).isWithin(0.1f).of(1.2f) }
    }

    @Test
    fun isWithin_failsOnNonFiniteAndNull() {
        fails { assertThat(Double.NaN).isWithin(1.0).of(0.0) }
        fails { assertThat(Double.POSITIVE_INFINITY).isWithin(1.0).of(Double.POSITIVE_INFINITY) }
        fails { assertThat(null as Double?).isWithin(1.0).of(0.0) }
        assertFailsWith<IllegalArgumentException> { assertThat(0.0).isWithin(-1.0) }
    }

    @Test
    fun isEqualTo_usesEqualsButComparesIntegralTypesByValue() {
        assertThat("a").isEqualTo("a")
        fails { assertThat("a").isEqualTo("b") }
        assertThat(3L).isEqualTo(3)
        assertThat(3).isEqualTo(3L)
        fails { assertThat(3L).isEqualTo(4) }
        // Doubles are not integral: Truth does not equate 1.0 with 1, and -0.0 is not 0.0.
        fails { assertThat(1.0).isEqualTo(1) }
        fails { assertThat(-0.0).isEqualTo(0.0) }
        assertThat(Double.NaN).isEqualTo(Double.NaN)
        assertThat("a").isNotEqualTo("b")
        fails { assertThat("a").isNotEqualTo("a") }
    }

    @Test
    fun nullChecks() {
        assertThat(null as Any?).isNull()
        fails { assertThat(Any()).isNull() }
        assertThat(Any()).isNotNull()
        fails { assertThat(null as Any?).isNotNull() }
    }

    @Test
    fun booleans() {
        assertThat(true).isTrue()
        assertThat(false).isFalse()
        fails { assertThat(false).isTrue() }
        fails { assertThat(true).isFalse() }
        fails { assertThat(null as Boolean?).isTrue() }
    }

    @Test
    fun comparisons() {
        assertThat(1).isLessThan(2)
        fails { assertThat(2).isLessThan(2) }
        assertThat(3.0).isGreaterThan(2.0)
        fails { assertThat(2.0).isGreaterThan(2.0) }
        assertThat(2).isAtMost(2)
        fails { assertThat(3).isAtMost(2) }
        assertThat(2L).isAtLeast(2L)
        fails { assertThat(1L).isAtLeast(2L) }
    }

    @Test
    fun isInstanceOf() {
        assertThat("s" as Any).isInstanceOf(String::class)
        fails { assertThat(1 as Any).isInstanceOf(String::class) }
        fails { assertThat(null as Any?).isInstanceOf(String::class) }
    }

    @Test
    fun containsExactly_ignoresOrderUnlessInOrder() {
        assertThat(listOf(1, 2, 2)).containsExactly(2, 1, 2)
        assertThat(listOf(1, 2)).containsExactly(1, 2).inOrder()
        fails { assertThat(listOf(1, 2)).containsExactly(2, 1).inOrder() }
        fails { assertThat(listOf(1, 2, 2)).containsExactly(1, 2) }
        fails { assertThat(listOf(1, 2)).containsExactly(1, 2, 2) }
        fails { assertThat(listOf(1)).containsExactly(2) }
        assertThat(setOf("a", "b")).containsExactlyElementsIn(listOf("b", "a"))
    }

    @Test
    fun iterables() {
        assertThat(emptyList<Int>()).isEmpty()
        fails { assertThat(listOf(1)).isEmpty() }
        assertThat(listOf(1)).isNotEmpty()
        fails { assertThat(emptyList<Int>()).isNotEmpty() }
        assertThat(listOf(1, 2)).hasSize(2)
        fails { assertThat(listOf(1, 2)).hasSize(3) }
        assertThat(listOf(1, 2)).contains(2)
        fails { assertThat(listOf(1, 2)).contains(3) }
        assertThat(listOf(1, 2, 2)).isInOrder()
        fails { assertThat(listOf(2, 1)).isInOrder() }
        assertThat(listOf(1, 2, 3)).isInStrictOrder()
        fails { assertThat(listOf(1, 2, 2)).isInStrictOrder() }
        assertThat(listOf(3, 2, 1)).isInStrictOrder(reverseOrder<Int>())
        fails { assertThat(listOf(1, 2)).isInStrictOrder(reverseOrder<Int>()) }
        assertThat(listOf(2, 2, 1)).isInOrder(reverseOrder<Int>())
        fails { assertThat(listOf(1, 2)).isInOrder(reverseOrder<Int>()) }
    }

    @Test
    fun maps() {
        val map = mapOf("a" to 1)
        assertThat(map).containsKey("a")
        fails { assertThat(map).containsKey("b") }
        assertThat(map).doesNotContainKey("b")
        fails { assertThat(map).doesNotContainKey("a") }
        assertThat(map).hasSize(1)
        assertThat(emptyMap<String, Int>()).isEmpty()
    }

    @Test
    fun strings() {
        assertThat("").isEmpty()
        fails { assertThat("x").isEmpty() }
        assertThat("hello").contains("ell")
        fails { assertThat("hello").contains("xyz") }
        assertThat("x").isNotEmpty()
        fails { assertThat("").isNotEmpty() }
    }

    @Test
    fun assertWithMessage_prefixesTheFailure() {
        val error =
            assertFailsWith<AssertionError> {
                assertWithMessage("star x").that(1.0).isWithin(0.1).of(2.0)
            }
        assertEquals("star x", error.message!!.lineSequence().first())
        assertTrue(error.message!!.contains("but was: 1.0"))
    }
}
