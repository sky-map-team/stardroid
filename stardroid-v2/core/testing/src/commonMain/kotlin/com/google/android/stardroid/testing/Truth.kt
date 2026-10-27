/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.testing

import kotlin.math.abs
import kotlin.reflect.KClass
import kotlin.test.fail

/*
 * A multiplatform stand-in for the slice of Google Truth the pure modules' tests use. Truth is
 * JVM-only, and these suites — the ephemeris, rise/set and projection goldens above all — have to
 * run on the iOS targets too, since that is the correctness guarantee the shared core exists for.
 *
 * The names and semantics are Truth's, so a suite moves to commonTest by changing its imports and
 * nothing else: `containsExactly` ignores order unless followed by `inOrder()`, `isEqualTo` treats
 * integral types by value (a Long equals an Int of the same value), and `isWithin(t).of(e)` fails
 * on NaN and infinities. Grow it only when a test needs something; it is not meant to be Truth.
 */

fun assertThat(actual: Double?): DoubleSubject = StandardSubjectBuilder(null).that(actual)

fun assertThat(actual: Float?): FloatSubject = StandardSubjectBuilder(null).that(actual)

fun assertThat(actual: Boolean?): BooleanSubject = StandardSubjectBuilder(null).that(actual)

fun assertThat(actual: String?): StringSubject = StandardSubjectBuilder(null).that(actual)

fun <T : Comparable<T>> assertThat(actual: T?): ComparableSubject<T> =
    StandardSubjectBuilder(null).that(actual)

fun assertThat(actual: Iterable<*>?): IterableSubject = StandardSubjectBuilder(null).that(actual)

fun assertThat(actual: Map<*, *>?): MapSubject = StandardSubjectBuilder(null).that(actual)

fun assertThat(actual: Any?): Subject = StandardSubjectBuilder(null).that(actual)

/** Truth's `assertWithMessage(message).that(actual)`: [message] prefixes any failure. */
fun assertWithMessage(message: String): StandardSubjectBuilder = StandardSubjectBuilder(message)

class StandardSubjectBuilder internal constructor(
    private val message: String?,
) {
    fun that(actual: Double?): DoubleSubject = DoubleSubject(actual, message)

    fun that(actual: Float?): FloatSubject = FloatSubject(actual, message)

    fun that(actual: Boolean?): BooleanSubject = BooleanSubject(actual, message)

    fun that(actual: String?): StringSubject = StringSubject(actual, message)

    fun <T : Comparable<T>> that(actual: T?): ComparableSubject<T> =
        ComparableSubject(actual, message)

    fun that(actual: Iterable<*>?): IterableSubject = IterableSubject(actual, message)

    fun that(actual: Map<*, *>?): MapSubject = MapSubject(actual, message)

    fun that(actual: Any?): Subject = Subject(actual, message)
}

open class Subject internal constructor(
    private val actual: Any?,
    private val message: String?,
) {
    fun isEqualTo(expected: Any?) {
        if (!valuesEqual(actual, expected)) failExpected("expected", expected)
    }

    fun isNotEqualTo(unexpected: Any?) {
        if (valuesEqual(actual, unexpected)) failExpected("expected not to be", unexpected)
    }

    fun isNull() {
        if (actual != null) failExpected("expected", null)
    }

    fun isNotNull() {
        if (actual == null) failWith("expected a non-null value")
    }

    fun isInstanceOf(type: KClass<*>) {
        if (!type.isInstance(actual)) failExpected("expected an instance of", type.simpleName)
    }

    /** Fails with [fact], followed by the actual value, prefixed with any assertWithMessage. */
    protected fun failWith(fact: String): Nothing =
        fail(listOfNotNull(message, fact, "but was: ${show(actual)}").joinToString("\n"))

    protected fun failExpected(
        verb: String,
        expected: Any?,
    ): Nothing = failWith("$verb: ${show(expected)}")
}

open class ComparableSubject<T : Comparable<T>> internal constructor(
    private val actual: T?,
    message: String?,
) : Subject(actual, message) {
    fun isLessThan(other: T) {
        if (nonNull() >= other) failExpected("expected to be less than", other)
    }

    fun isGreaterThan(other: T) {
        if (nonNull() <= other) failExpected("expected to be greater than", other)
    }

    fun isAtMost(other: T) {
        if (nonNull() > other) failExpected("expected to be at most", other)
    }

    fun isAtLeast(other: T) {
        if (nonNull() < other) failExpected("expected to be at least", other)
    }

    /** Truth's `isIn(Range)`, over Kotlin's closed range: `0.0..1.0`. */
    fun isIn(range: ClosedRange<T>) {
        if (nonNull() !in range) failExpected("expected to be in range", range)
    }

    /** Truth's `isIn(Range.closedOpen(…))`, over Kotlin's open-ended range: `0.0..<360.0`. */
    fun isIn(range: OpenEndRange<T>) {
        if (nonNull() !in range) failExpected("expected to be in range", range)
    }

    protected fun nonNull(): T = actual ?: failWith("expected a non-null value")
}

class DoubleSubject internal constructor(
    private val actual: Double?,
    message: String?,
) : ComparableSubject<Double>(actual, message) {
    fun isWithin(tolerance: Double): TolerantComparison {
        require(tolerance >= 0.0 && tolerance.isFinite()) { "tolerance ($tolerance) is invalid" }
        return TolerantComparison { expected ->
            val value = nonNull()
            // Negated <= so NaN (and ∞ − ∞) fail, as in Truth.
            if (!(abs(value - expected) <= tolerance)) {
                failWith("expected: $expected (within $tolerance)")
            }
        }
    }

    /** Not 0.0 or -0.0 (NaN passes), as in Truth. */
    fun isNonZero() {
        if (nonNull() == 0.0) failWith("expected not to be zero")
    }

    fun interface TolerantComparison {
        fun of(expected: Double)
    }
}

class FloatSubject internal constructor(
    private val actual: Float?,
    message: String?,
) : ComparableSubject<Float>(actual, message) {
    fun isWithin(tolerance: Float): TolerantComparison {
        require(tolerance >= 0f && tolerance.isFinite()) { "tolerance ($tolerance) is invalid" }
        return TolerantComparison { expected ->
            val value = nonNull()
            if (!(abs(value - expected) <= tolerance)) {
                failWith("expected: $expected (within $tolerance)")
            }
        }
    }

    fun interface TolerantComparison {
        fun of(expected: Float)
    }
}

class BooleanSubject internal constructor(
    private val actual: Boolean?,
    message: String?,
) : Subject(actual, message) {
    fun isTrue() {
        if (actual != true) failWith("expected to be true")
    }

    fun isFalse() {
        if (actual != false) failWith("expected to be false")
    }
}

class StringSubject internal constructor(
    private val actual: String?,
    message: String?,
) : ComparableSubject<String>(actual, message) {
    fun isEmpty() {
        if (nonNull().isNotEmpty()) failWith("expected to be empty")
    }

    fun isNotEmpty() {
        if (nonNull().isEmpty()) failWith("expected not to be empty")
    }

    fun contains(sequence: CharSequence) {
        if (!nonNull().contains(sequence)) failExpected("expected to contain", sequence)
    }

    fun startsWith(prefix: String) {
        if (!nonNull().startsWith(prefix)) failExpected("expected to start with", prefix)
    }

    fun endsWith(suffix: String) {
        if (!nonNull().endsWith(suffix)) failExpected("expected to end with", suffix)
    }
}

class IterableSubject internal constructor(
    private val actual: Iterable<*>?,
    message: String?,
) : Subject(actual, message) {
    fun isEmpty() {
        if (!nonNull().none()) failWith("expected to be empty")
    }

    fun isNotEmpty() {
        if (nonNull().none()) failWith("expected not to be empty")
    }

    fun hasSize(size: Int) {
        if (nonNull().count() != size) failWith("expected to have size: $size")
    }

    fun contains(element: Any?) {
        if (nonNull().none { valuesEqual(it, element) }) {
            failExpected("expected to contain", element)
        }
    }

    fun doesNotContain(element: Any?) {
        if (nonNull().any { valuesEqual(it, element) }) {
            failExpected("expected not to contain", element)
        }
    }

    /** Every one of [expected], in any order, among possibly others (multiplicities count). */
    fun containsAtLeast(vararg expected: Any?) = containsAtLeastElementsIn(expected.asList())

    fun containsAtLeastElementsIn(expected: Iterable<*>) {
        val unmatched = nonNull().toMutableList()
        val missing = expected.filter { e -> !unmatched.removeFirstMatch(e) }
        if (missing.isNotEmpty()) {
            failWith(
                "expected to contain at least: ${show(
                    expected.toList(),
                )}\nmissing: ${show(missing)}",
            )
        }
    }

    /** Same elements with the same multiplicities, in any order unless [Ordered.inOrder]. */
    fun containsExactly(vararg expected: Any?): Ordered =
        containsExactlyElementsIn(expected.asList())

    fun containsExactlyElementsIn(expected: Iterable<*>): Ordered {
        val actualList = nonNull().toList()
        val expectedList = expected.toList()
        val unmatched = actualList.toMutableList()
        val missing = expectedList.filter { e -> !unmatched.removeFirstMatch(e) }
        if (missing.isNotEmpty() || unmatched.isNotEmpty()) {
            failWith(
                "expected exactly: ${show(expectedList)}\n" +
                    "missing: ${show(missing)}\nunexpected: ${show(unmatched)}",
            )
        }
        return Ordered {
            val inOrder =
                actualList.size == expectedList.size &&
                    actualList.zip(expectedList).all { (a, e) -> valuesEqual(a, e) }
            if (!inOrder) failWith("expected in order: ${show(expectedList)}")
        }
    }

    /** Each element is ≤ the next under natural ordering. */
    fun isInOrder() = isInOrder(naturalOrder)

    /** Each element is ≤ the next under [comparator]. */
    fun isInOrder(comparator: Comparator<*>) =
        checkPairs("expected to be in order") { a, b -> comparator.compareAny(a, b) <= 0 }

    /** Each element is < the next under natural ordering. */
    fun isInStrictOrder() = isInStrictOrder(naturalOrder)

    /** Each element is < the next under [comparator]. */
    fun isInStrictOrder(comparator: Comparator<*>) =
        checkPairs("expected to be in strict order") { a, b -> comparator.compareAny(a, b) < 0 }

    private fun checkPairs(
        fact: String,
        ok: (Any?, Any?) -> Boolean,
    ) {
        if (!nonNull().zipWithNext().all { (a, b) -> ok(a, b) }) failWith(fact)
    }

    private fun nonNull(): Iterable<*> = actual ?: failWith("expected a non-null iterable")

    fun interface Ordered {
        fun inOrder()
    }
}

class MapSubject internal constructor(
    private val actual: Map<*, *>?,
    message: String?,
) : Subject(actual, message) {
    fun isEmpty() {
        if (nonNull().isNotEmpty()) failWith("expected to be empty")
    }

    fun isNotEmpty() {
        if (nonNull().isEmpty()) failWith("expected not to be empty")
    }

    fun hasSize(size: Int) {
        if (nonNull().size != size) failWith("expected to have size: $size")
    }

    fun containsKey(key: Any?) {
        if (!nonNull().containsKey(key)) failExpected("expected to contain key", key)
    }

    fun doesNotContainKey(key: Any?) {
        if (nonNull().containsKey(key)) failExpected("expected not to contain key", key)
    }

    fun containsEntry(
        key: Any?,
        value: Any?,
    ) {
        val map = nonNull()
        if (!map.containsKey(key) || !valuesEqual(map[key], value)) {
            failExpected("expected to contain entry", "$key=$value")
        }
    }

    /**
     * Truth's alternating `containsExactly(k0, v0, k1, v1, …)`: exactly these entries, in any
     * order unless [IterableSubject.Ordered.inOrder] (iteration order, for an ordered map).
     */
    fun containsExactly(vararg keysAndValues: Any?): IterableSubject.Ordered {
        require(keysAndValues.size % 2 == 0) { "containsExactly takes alternating keys and values" }
        val expected = keysAndValues.toList().chunked(2) { (k, v) -> k to v }
        val actualEntries = nonNull().entries.map { it.key to it.value }
        val unmatched: MutableList<Any?> = actualEntries.toMutableList()
        val missing = expected.filter { e -> !unmatched.removeFirstMatch(e) }
        if (missing.isNotEmpty() || unmatched.isNotEmpty()) {
            failWith(
                "expected exactly: ${show(expected)}\nmissing: ${show(missing)}" +
                    "\nunexpected: ${show(unmatched)}",
            )
        }
        return IterableSubject.Ordered {
            if (actualEntries != expected) failWith("expected in order: ${show(expected)}")
        }
    }

    private fun nonNull(): Map<*, *> = actual ?: failWith("expected a non-null map")
}

/** Truth's equality: `equals`, except integral numbers compare by value across their types. */
private fun valuesEqual(
    a: Any?,
    b: Any?,
): Boolean {
    val integralA = a.asIntegral()
    val integralB = b.asIntegral()
    return if (integralA != null && integralB != null) integralA == integralB else a == b
}

private fun Any?.asIntegral(): Long? =
    when (this) {
        is Byte -> toLong()
        is Short -> toLong()
        is Int -> toLong()
        is Long -> this
        else -> null
    }

@Suppress("UNCHECKED_CAST")
private val naturalOrder: Comparator<*> =
    Comparator<Any?> {
            a,
            b,
        ->
        (a as Comparable<Any?>).compareTo(b)
    }

// Truth takes Comparator<?> too; the elements' type is unchecked either way.
@Suppress("UNCHECKED_CAST")
private fun Comparator<*>.compareAny(
    a: Any?,
    b: Any?,
): Int = (this as Comparator<Any?>).compare(a, b)

private fun MutableList<Any?>.removeFirstMatch(element: Any?): Boolean {
    val index = indexOfFirst { valuesEqual(it, element) }
    if (index >= 0) removeAt(index)
    return index >= 0
}

private fun show(value: Any?): String = if (value is String) "\"$value\"" else value.toString()
