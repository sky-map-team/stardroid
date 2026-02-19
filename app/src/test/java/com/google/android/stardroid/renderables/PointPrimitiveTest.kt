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

package com.google.android.stardroid.renderables

import android.graphics.Color
import com.google.android.stardroid.math.Vector3
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [PointPrimitive].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PointPrimitiveTest {

    @Test
    fun constructor_withVector3_setsAllFields() {
        val coords = Vector3(1f, 2f, 3f)
        val color = Color.RED
        val size = 5

        val point = PointPrimitive(coords, color, size)

        assertThat(point.location).isEqualTo(coords)
        assertThat(point.color).isEqualTo(color)
        assertThat(point.size).isEqualTo(size)
    }

    @Test
    fun constructor_withVector3_defaultsToCircle() {
        val point = PointPrimitive(Vector3(1f, 0f, 0f), Color.WHITE, 3)

        assertThat(point.pointShape).isEqualTo(PointPrimitive.Shape.CIRCLE)
    }

    @Test
    fun constructor_withShape_usesProvidedShape() {
        val point = PointPrimitive(
            Vector3(1f, 0f, 0f),
            Color.WHITE,
            3,
            PointPrimitive.Shape.STAR
        )

        assertThat(point.pointShape).isEqualTo(PointPrimitive.Shape.STAR)
    }

    @Test
    fun constructor_withRaDec_calculatesCoords() {
        // RA = 0, Dec = 0 should give us a point on the x-axis
        val point = PointPrimitive(0f, 0f, Color.WHITE, 3)

        // Verify coordinates are on the unit sphere
        val coords = point.location
        val magnitude = kotlin.math.sqrt(
            coords.x * coords.x + coords.y * coords.y + coords.z * coords.z
        )
        assertThat(magnitude).isWithin(0.001f).of(1f)
    }

    @Test
    fun getSize_returnsSize() {
        val point = PointPrimitive(Vector3(0f, 0f, 1f), Color.BLUE, 7)

        assertThat(point.getSize()).isEqualTo(7)
    }

    @Test
    fun getPointShape_returnsShape() {
        val point = PointPrimitive(
            Vector3(1f, 0f, 0f),
            Color.WHITE,
            3,
            PointPrimitive.Shape.NEBULA
        )

        assertThat(point.getPointShape()).isEqualTo(PointPrimitive.Shape.NEBULA)
    }

    @Test
    fun shapeEnum_allShapesExist() {
        // Verify all expected shapes are present
        assertThat(PointPrimitive.Shape.values()).hasLength(10)
        assertThat(PointPrimitive.Shape.CIRCLE).isNotNull()
        assertThat(PointPrimitive.Shape.STAR).isNotNull()
        assertThat(PointPrimitive.Shape.ELLIPTICAL_GALAXY).isNotNull()
        assertThat(PointPrimitive.Shape.SPIRAL_GALAXY).isNotNull()
        assertThat(PointPrimitive.Shape.IRREGULAR_GALAXY).isNotNull()
        assertThat(PointPrimitive.Shape.LENTICULAR_GALAXY).isNotNull()
        assertThat(PointPrimitive.Shape.GLOBULAR_CLUSTER).isNotNull()
        assertThat(PointPrimitive.Shape.OPEN_CLUSTER).isNotNull()
        assertThat(PointPrimitive.Shape.NEBULA).isNotNull()
        assertThat(PointPrimitive.Shape.HUBBLE_DEEP_FIELD).isNotNull()
    }

    @Test
    fun shapeImageIndex_currentlyReturnsZero() {
        // Note: getImageIndex() currently returns 0 for all shapes (see implementation)
        // This test documents the current behavior
        assertThat(PointPrimitive.Shape.CIRCLE.imageIndex).isEqualTo(0)
        assertThat(PointPrimitive.Shape.STAR.imageIndex).isEqualTo(0)
        assertThat(PointPrimitive.Shape.NEBULA.imageIndex).isEqualTo(0)
    }
}
