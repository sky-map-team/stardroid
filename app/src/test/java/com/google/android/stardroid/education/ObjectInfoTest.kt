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
        assertThat(info.type).isEqualTo(ObjectType.STAR) // default
        assertThat(info.distance).isNull()
        assertThat(info.size).isNull()
        assertThat(info.mass).isNull()
    }

    @Test
    fun testObjectInfoWithScientificData() {
        val info = ObjectInfo(
            id = "betelgeuse",
            name = "Betelgeuse",
            description = "A red supergiant star",
            funFact = "May explode soon",
            type = ObjectType.STAR,
            distance = "~700 light-years",
            size = "~900 solar radii",
            mass = "~12 solar masses",
            spectralClass = "M1.5Iab",
            magnitude = "0.42"
        )

        assertThat(info.type).isEqualTo(ObjectType.STAR)
        assertThat(info.distance).isEqualTo("~700 light-years")
        assertThat(info.size).isEqualTo("~900 solar radii")
        assertThat(info.mass).isEqualTo("~12 solar masses")
        assertThat(info.spectralClass).isEqualTo("M1.5Iab")
        assertThat(info.magnitude).isEqualTo("0.42")
    }

    @Test
    fun testObjectInfoEquality() {
        val info1 = ObjectInfo("sun", "Sun", "Our star", "Very hot", ObjectType.STAR)
        val info2 = ObjectInfo("sun", "Sun", "Our star", "Very hot", ObjectType.STAR)
        val info3 = ObjectInfo("moon", "Moon", "Our satellite", "Tidal lock", ObjectType.MOON)

        assertThat(info1).isEqualTo(info2)
        assertThat(info1).isNotEqualTo(info3)
    }

    @Test
    fun testObjectInfoCopy() {
        val original = ObjectInfo(
            "venus", "Venus", "Morning star", "Hottest planet",
            ObjectType.PLANET, "108M km", "12,104 km", "4.87 × 10²⁴ kg"
        )
        val copy = original.copy(name = "Vênus")

        assertThat(copy.id).isEqualTo(original.id)
        assertThat(copy.name).isEqualTo("Vênus")
        assertThat(copy.description).isEqualTo(original.description)
        assertThat(copy.type).isEqualTo(ObjectType.PLANET)
        assertThat(copy.distance).isEqualTo("108M km")
    }

    @Test
    fun testObjectTypeValues() {
        assertThat(ObjectType.values()).hasLength(8)
        assertThat(ObjectType.PLANET.name).isEqualTo("PLANET")
        assertThat(ObjectType.STAR.name).isEqualTo("STAR")
        assertThat(ObjectType.MOON.name).isEqualTo("MOON")
        assertThat(ObjectType.DWARF_PLANET.name).isEqualTo("DWARF_PLANET")
        assertThat(ObjectType.NEBULA.name).isEqualTo("NEBULA")
        assertThat(ObjectType.GALAXY.name).isEqualTo("GALAXY")
        assertThat(ObjectType.CLUSTER.name).isEqualTo("CLUSTER")
        assertThat(ObjectType.CONSTELLATION.name).isEqualTo("CONSTELLATION")
    }
}
