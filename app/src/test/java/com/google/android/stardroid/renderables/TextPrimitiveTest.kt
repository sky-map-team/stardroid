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
 * Tests for [TextPrimitive].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TextPrimitiveTest {

    @Test
    fun constructor_withVector3_setsAllFields() {
        val coords = Vector3(1f, 0f, 0f)
        val label = "Mars"
        val color = Color.RED

        val text = TextPrimitive(coords, label, color)

        assertThat(text.location).isEqualTo(coords)
        assertThat(text.label).isEqualTo(label)
        assertThat(text.color).isEqualTo(color)
    }

    @Test
    fun constructor_withDefaults_setsDefaultOffset() {
        val text = TextPrimitive(Vector3(0f, 0f, 1f), "Test", Color.WHITE)

        assertThat(text.offset).isWithin(0.001f).of(0.02f)
    }

    @Test
    fun constructor_withDefaults_setsDefaultFontSize() {
        val text = TextPrimitive(Vector3(0f, 0f, 1f), "Test", Color.WHITE)

        assertThat(text.fontSize).isEqualTo(15)
    }

    @Test
    fun constructor_withCustomOffset_setsOffset() {
        val text = TextPrimitive(Vector3(0f, 0f, 1f), "Test", Color.WHITE, 0.05f, 20)

        assertThat(text.offset).isWithin(0.001f).of(0.05f)
    }

    @Test
    fun constructor_withCustomFontSize_setsFontSize() {
        val text = TextPrimitive(Vector3(0f, 0f, 1f), "Test", Color.WHITE, 0.02f, 24)

        assertThat(text.fontSize).isEqualTo(24)
    }

    @Test(expected = NullPointerException::class)
    fun constructor_withNullLabel_throws() {
        TextPrimitive(Vector3(0f, 0f, 1f), null, Color.WHITE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_withEmptyLabel_throws() {
        TextPrimitive(Vector3(0f, 0f, 1f), "", Color.WHITE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_withWhitespaceLabel_throws() {
        TextPrimitive(Vector3(0f, 0f, 1f), "   ", Color.WHITE)
    }

    @Test
    fun constructor_withRaDec_calculatesCoords() {
        // RA = 0, Dec = 0 should give us a point on the unit sphere
        val text = TextPrimitive(0f, 0f, "Test", Color.WHITE)

        val coords = text.location
        val magnitude = kotlin.math.sqrt(
            coords.x * coords.x + coords.y * coords.y + coords.z * coords.z
        )
        assertThat(magnitude).isWithin(0.001f).of(1f)
    }

    @Test
    fun getText_returnsLabel() {
        val text = TextPrimitive(Vector3(0f, 0f, 1f), "Saturn", Color.YELLOW)

        assertThat(text.getText()).isEqualTo("Saturn")
    }

    @Test
    fun getFontSize_returnsFontSize() {
        val text = TextPrimitive(Vector3(0f, 0f, 1f), "Test", Color.WHITE, 0.02f, 18)

        assertThat(text.getFontSize()).isEqualTo(18)
    }

    @Test
    fun getOffset_returnsOffset() {
        val text = TextPrimitive(Vector3(0f, 0f, 1f), "Test", Color.WHITE, 0.03f, 15)

        assertThat(text.getOffset()).isWithin(0.001f).of(0.03f)
    }

    @Test
    fun setText_updatesLabel() {
        val text = TextPrimitive(Vector3(0f, 0f, 1f), "Original", Color.WHITE)

        text.setText("Updated")

        assertThat(text.getText()).isEqualTo("Updated")
        assertThat(text.label).isEqualTo("Updated")
    }
}
