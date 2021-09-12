// Copyright 2008 Google Inc.
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
package com.google.android.stardroid.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LatLong2Test {
    @Test
    fun testDistanceFrom90Degrees() {
        val l1 = LatLong(0f, 0f)
        val l2 = LatLong(0f, 90f)
        assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(90f)
    }

    @Test
    fun testDistanceFromSame() {
        val l1 = LatLong(30f, 9f)
        val l2 = LatLong(30f, 9f)
        assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(0f)
    }

    @Test
    fun testDistanceFromOppositePoles() {
        val l1 = LatLong(-90f, 45f)
        val l2 = LatLong(90f, 45f)
        assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(180f)
    }

    @Test
    fun testDistanceFromOnEquator() {
        val l1 = LatLong(0f, -20f)
        val l2 = LatLong(0f, 30f)
        assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(50f)
    }

    @Test
    fun testDistanceFromOnMeridian() {
        val l1 = LatLong(-10f, 0f)
        val l2 = LatLong(40f, 0f)
        assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(50f)
    }

    companion object {
        private const val TOL = 1e-4f
    }
}