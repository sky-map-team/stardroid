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
package com.google.android.stardroid.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val TOL = 0.00001f

class AstronomyTest {
    @Test
    fun testCalculateRotationMatrix() {
        val noRotation = calculateRotationMatrix(0f, Vector3(1f, 2f, 3f))
        val identity = Matrix3x3(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        Matrix3x3Subject.assertThat(identity).isWithin(TOL).of(noRotation)
        val rotAboutZ = calculateRotationMatrix(90f, Vector3(0f, 0f, 1f))
        Matrix3x3Subject.assertThat(Matrix3x3(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)).isWithin(TOL)
            .of(rotAboutZ)
        val axis = Vector3(2f, -4f, 1f)
        axis.normalize()
        val rotA = calculateRotationMatrix(30f, axis)
        val rotB = calculateRotationMatrix(-30f, axis)
        val shouldBeIdentity = rotA * rotB
        Matrix3x3Subject.assertThat(identity).isWithin(TOL).of(shouldBeIdentity)
        val axisPerpendicular = Vector3(4f, 2f, 0f)
        val rotatedAxisPerpendicular = rotA * axisPerpendicular

        // Should still be perpendicular
        assertThat(axis dot rotatedAxisPerpendicular).isWithin(TOL).of(0.0f)
        // And the angle between them should be 30 degrees
        axisPerpendicular.normalize()
        rotatedAxisPerpendicular.normalize()
        assertThat(axisPerpendicular dot rotatedAxisPerpendicular).isWithin(TOL)
            .of(Math.cos(30.0 * DEGREES_TO_RADIANS).toFloat())
    }
}
