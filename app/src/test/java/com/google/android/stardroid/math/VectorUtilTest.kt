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

private const val TOL = 0.00001f

class VectorUtilTest {
    @Test
    fun testDotProduct() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(0.3f, 0.4f, 0.5f)
        val dp = VectorUtil.dotProduct(v1, v2)
        assertThat(dp).isWithin(TOL).of(2.6f)
    }
}