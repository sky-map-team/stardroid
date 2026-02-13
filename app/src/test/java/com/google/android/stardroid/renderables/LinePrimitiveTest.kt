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
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [LinePrimitive].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinePrimitiveTest {

    @Test
    fun defaultConstructor_createsWhiteLine() {
        val line = LinePrimitive()

        assertThat(line.color).isEqualTo(Color.WHITE)
    }

    @Test
    fun defaultConstructor_hasDefaultLineWidth() {
        val line = LinePrimitive()

        assertThat(line.lineWidth).isWithin(0.01f).of(1.5f)
    }

    @Test
    fun defaultConstructor_hasEmptyVertices() {
        val line = LinePrimitive()

        assertThat(line.getVertices()).isEmpty()
    }

    @Test
    fun constructor_withColor_setsColor() {
        val line = LinePrimitive(Color.BLUE)

        assertThat(line.color).isEqualTo(Color.BLUE)
    }

    @Test
    fun constructor_withVertices_storesVertices() {
        val vertices = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 0f, 0f),
            Vector3(1f, 1f, 0f)
        )

        val line = LinePrimitive(Color.RED, vertices, 2.0f)

        assertThat(line.getVertices()).hasSize(3)
        assertThat(line.getVertices()).containsExactlyElementsIn(vertices).inOrder()
    }

    @Test
    fun constructor_withLineWidth_setsLineWidth() {
        val line = LinePrimitive(Color.GREEN, ArrayList(), 3.5f)

        assertThat(line.lineWidth).isWithin(0.01f).of(3.5f)
    }

    @Test
    fun getVertices_returnsUnmodifiableList() {
        val vertices = mutableListOf(Vector3(1f, 2f, 3f))
        val line = LinePrimitive(Color.WHITE, vertices, 1.0f)

        val result = line.getVertices()

        // Attempting to modify the result should throw
        assertThrows(UnsupportedOperationException::class.java) {
            result.add(Vector3(4f, 5f, 6f))
        }

    }

    @Test
    fun getLineWidth_returnsLineWidth() {
        val line = LinePrimitive(Color.CYAN, ArrayList(), 2.5f)

        assertThat(line.getLineWidth()).isWithin(0.01f).of(2.5f)
    }

    @Test
    fun vertices_directAccess_sameAsGetter() {
        val vertices = listOf(Vector3(1f, 0f, 0f))
        val line = LinePrimitive(Color.WHITE, vertices, 1.0f)

        // Direct field access and getter should return same content
        assertThat(line.vertices).containsExactlyElementsIn(line.getVertices())
    }
}
