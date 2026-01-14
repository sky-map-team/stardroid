// Copyright 2024 Google Inc.
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
package com.google.android.stardroid.education

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [ObjectInfo] data class.
 */
class ObjectInfoTest {

    @Test
    fun testObjectInfoCreation() {
        val info = ObjectInfo(
            id = "mars",
            name = "Mars",
            description = "The Red Planet",
            funFact = "Mars has the largest volcano"
        )

        assertThat(info.id).isEqualTo("mars")
        assertThat(info.name).isEqualTo("Mars")
        assertThat(info.description).isEqualTo("The Red Planet")
        assertThat(info.funFact).isEqualTo("Mars has the largest volcano")
    }

    @Test
    fun testObjectInfoEquality() {
        val info1 = ObjectInfo("sun", "Sun", "Our star", "Very hot")
        val info2 = ObjectInfo("sun", "Sun", "Our star", "Very hot")
        val info3 = ObjectInfo("moon", "Moon", "Our satellite", "Tidal lock")

        assertThat(info1).isEqualTo(info2)
        assertThat(info1).isNotEqualTo(info3)
    }

    @Test
    fun testObjectInfoCopy() {
        val original = ObjectInfo("venus", "Venus", "Morning star", "Hottest planet")
        val copy = original.copy(name = "Vênus")

        assertThat(copy.id).isEqualTo(original.id)
        assertThat(copy.name).isEqualTo("Vênus")
        assertThat(copy.description).isEqualTo(original.description)
    }
}
