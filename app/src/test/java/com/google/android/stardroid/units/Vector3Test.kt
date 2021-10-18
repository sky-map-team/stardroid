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
package com.google.android.stardroid.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Vector3Test {
    private val TOL = 1e-9f

    @Test
    fun testEquals() {
        val one = Vector3(1f, 2f, 3f)
        val two = Vector3(2f, 4f, 6f)
        one.scale(2f)
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
    fun testNorm() {
        val pythag = Vector3(3f, 4f, 0f)
        pythag.normalize()
        assertThat(pythag.length).isWithin(TOL).of(1f)
        assertThat(pythag.x / pythag.y).isWithin(TOL).of(3f/4f)
    }
}