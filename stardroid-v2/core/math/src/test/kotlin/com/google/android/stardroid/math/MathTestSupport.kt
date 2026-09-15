/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.math

import com.google.common.truth.Truth.assertThat

/** The nine elements in row-major order, for element-wise comparison in tests. */
internal fun Matrix3.elements() = listOf(xx, xy, xz, yx, yy, yz, zx, zy, zz)

/** Asserts two matrices agree element-wise within [tol]. */
internal fun assertClose(
    actual: Matrix3,
    expected: Matrix3,
    tol: Double = 1e-9,
) {
    actual.elements().zip(expected.elements()).forEach { (a, e) ->
        assertThat(a).isWithin(tol).of(e)
    }
}

/** Asserts two vectors agree component-wise within [tol]. */
internal fun assertClose(
    actual: Vector3,
    expected: Vector3,
    tol: Double = 1e-9,
) {
    assertThat(actual.x).isWithin(tol).of(expected.x)
    assertThat(actual.y).isWithin(tol).of(expected.y)
    assertThat(actual.z).isWithin(tol).of(expected.z)
}
