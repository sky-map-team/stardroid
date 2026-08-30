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
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Randomized property checks for the math primitives (D6 testing strategy: "property tests for
 * the math types — rotation orthogonality, RaDec round-trips"). A fixed seed keeps failures
 * reproducible.
 */
class MathPropertiesTest {
    private val rng = Random(20260621)

    private fun randomUnitVector(): Vector3 =
        Vector3(rng.nextDouble(-1.0, 1.0), rng.nextDouble(-1.0, 1.0), rng.nextDouble(-1.0, 1.0))
            .normalized()

    @Test
    fun raDecRoundTripsThroughVector() {
        repeat(1000) {
            val ra = rng.nextDouble(0.0, 360.0)
            // Stay away from the poles, where ra is degenerate.
            val dec = rng.nextDouble(-89.0, 89.0)
            val result = RaDec.fromGeocentricVector(RaDec(ra, dec).toGeocentricVector())
            assertThat(result.raDeg).isWithin(1e-6).of(ra)
            assertThat(result.decDeg).isWithin(1e-6).of(dec)
        }
    }

    @Test
    fun toGeocentricVectorIsUnitLength() {
        repeat(1000) {
            val raDec = RaDec(rng.nextDouble(0.0, 360.0), rng.nextDouble(-90.0, 90.0))
            assertThat(raDec.toGeocentricVector().length).isWithin(1e-9).of(1.0)
        }
    }

    @Test
    fun rotationMatricesAreOrthogonalAndPreserveLength() {
        repeat(1000) {
            val rot = rotationMatrix(rng.nextDouble(-720.0, 720.0), randomUnitVector())
            // Orthogonal: R * Rᵀ == I.
            val product = rot * rot.transposed()
            val identityElems =
                listOf(
                    product.xx, product.yy, product.zz,
                    product.xy, product.xz, product.yx, product.yz, product.zx, product.zy,
                )
            val expected = listOf(1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
            identityElems.zip(expected).forEach { (a, e) -> assertThat(a).isWithin(1e-9).of(e) }
            // Rotation preserves the length of an arbitrary vector.
            val v = Vector3(rng.nextDouble(), rng.nextDouble(), rng.nextDouble())
            assertThat((rot * v).length).isWithin(1e-9).of(v.length)
        }
    }
}
