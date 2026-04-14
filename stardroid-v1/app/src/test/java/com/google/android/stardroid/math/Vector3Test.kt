// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val TOL = 1e-7f

class Vector3Test {
    @Test
    fun testEquals() {
        val one = Vector3(1f, 2f, 3f)
        val two = Vector3(2f, 4f, 6f)
        one *= 2f
        assertThat(one).isEqualTo(two)
        assertThat(one).isNotSameInstanceAs(two)
        assertThat(one.hashCode()).isEqualTo(two.hashCode())
    }

    @Test
    fun testLength() {
        val pythag = Vector3(3f, 4f, 0f)
        assertThat(pythag.length).isWithin(TOL).of(5f)
        assertThat(pythag.length2).isWithin(TOL).of(25f)
    }

    @Test
    fun testNormalize() {
        val v = Vector3(3f, 4f, 0f)
        v.normalize()
        assertThat(v.length).isWithin(TOL).of(1f)
        assertThat(v.x / v.y).isWithin(TOL).of(3f/4f)
    }

    @Test
    fun testNormalizedCopy() {
        val v = Vector3(3f, 4f, 0f)
        val v2 = v.normalizedCopy()
        assertThat(v2.length).isWithin(TOL).of(1f)
        assertThat(v2.x / v2.y).isWithin(TOL).of(3f/4f)
    }

    @Test
    fun testScale() {
        val v = Vector3(3f, 4f, 5f)
        v *= 2f
        Vector3Subject.assertThat(v).isWithin(TOL).of(Vector3(6f, 8f, 10f))
    }

    @Test
    fun testSubtract() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(-2f, 2f, 2f)
        v1 -= v2
        Vector3Subject.assertThat(v1).isWithin(TOL).of(Vector3(3f, 0f, 1f))
    }

    @Test
    fun testMinus() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(-2f, 2f, 2f)
        Vector3Subject.assertThat(v1 - v2).isWithin(TOL).of(Vector3(3f, 0f, 1f))
    }

    @Test
    fun testPlus() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(-2f, 2f, 2f)
        Vector3Subject.assertThat(v1 + v2).isWithin(TOL).of(
            Vector3(-1f, 4f, 5f))
    }

    @Test
    fun testTimes() {
        val v1 = Vector3(1f, 2f, 3f)
        Vector3Subject.assertThat(v1 * 5f).isWithin(TOL).of(
            Vector3(5f, 10f, 15f))
    }

    @Test
    fun testDiv() {
        val v1 = Vector3(4f, 2f, 6f)
        Vector3Subject.assertThat(v1 / 2f).isWithin(TOL).of(
            Vector3(2f, 1f, 3f))
    }

    @Test
    fun testCrossProduct() {
        Vector3Subject.assertThat(Vector3.unitX() * Vector3.unitY()).isWithin(TOL).of(
            Vector3.unitZ())
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(-1f, 3f, -3f)
        val v3 = v1 * v2
        assertThat(v1 dot v3).isWithin(TOL).of(0f)
        assertThat(v2 dot v3).isWithin(TOL).of(0f)
    }

    @Test
    fun testDistanceFrom() {
        val v1 = Vector3(1f, 2f, 5f)
        val v2 = Vector3(-2f, 2f, 1f)
        assertThat(v1.distanceFrom(v2)).isWithin(TOL).of(5f)
    }

    @Test
    fun testDotProduct() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(0.3f, 0.4f, 0.5f)
        val dp = v1 dot v2
        assertThat(dp).isWithin(TOL).of(2.6f)
    }

    @Test
    fun testProjectOnto() {
        val v1 = Vector3(1f, 1f, 2f)
        val v2 = Vector3(2f, 1f, -1f).normalizedCopy()
        val projection = v1.projectOnto(v2)
        Vector3Subject.assertThat(v2 * projection).isWithin(TOL).of(Vector3.zero())
        assertThat((v1 - projection) dot projection).isWithin(TOL).of(0f)
    }

    @Test
    fun testCosineSimilarity() {
        val v1 = Vector3(1f,2f, 3f)
        val v2 = Vector3(2f, -1f, 10f)
        assertThat(v1.cosineSimilarity(v2)).isWithin(TOL).of(0.78246075f)
    }

    @Test
    fun testVectorProduct() {
        // Why waste a good test?
        // Check that z is x X y
        val x = Vector3(1f, 0f, 0f)
        val y = Vector3(0f, 1f, 0f)
        val z = x * y
        Vector3Subject.assertThat(z).isWithin(TOL).of(Vector3(0f, 0f, 1f))

        // Check that a X b is perpendicular to a and b
        val a = Vector3(1f, -2f, 3f)
        val b = Vector3(2f, 0f, -4f)
        val c = a * b
        val aDotc = a dot c
        val bDotc = b dot c
        assertThat(aDotc).isWithin(TOL).of(0.0f)
        assertThat(bDotc).isWithin(TOL).of(0.0f)

        // Check that |a X b| is correct
        val v = Vector3(1f, 2f, 0f)
        val ww = x * v
        assertThat(ww dot ww).isWithin(TOL)
            .of(Math.pow(1f * Math.sqrt(5.0) * Math.sin(Math.atan(2.0)), 2.0).toFloat())
    }
}