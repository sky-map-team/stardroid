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

package com.google.android.stardroid.util;

import com.google.android.stardroid.units.Vector3;

import junit.framework.TestCase;

public class Matrix4x4Test extends TestCase {

  static final float DELTA = 0.00001f;

  Float[] wrap(float[] matrix) {
    Float[] m = new Float[matrix.length];
    for (int i = 0; i < matrix.length; i++) {
      m[i] = new Float(matrix[i]);
    }
    return m;
  }

  void assertVectorsEqual(Vector3 v1, Vector3 v2) {
    assertEquals(v1.x, v2.x, DELTA);
    assertEquals(v1.y, v2.y, DELTA);
    assertEquals(v1.z, v2.z, DELTA);
  }

  void assertMatEqual(Matrix4x4 mat1, Matrix4x4 mat2) {
    float[] m1 = mat1.getFloatArray();
    float[] m2 = mat2.getFloatArray();
    assertEquals(m1[0], m2[0], DELTA);
    assertEquals(m1[1], m2[1], DELTA);
    assertEquals(m1[2], m2[2], DELTA);
    assertEquals(m1[3], m2[3], DELTA);
    assertEquals(m1[4], m2[4], DELTA);
    assertEquals(m1[5], m2[5], DELTA);
    assertEquals(m1[6], m2[6], DELTA);
    assertEquals(m1[7], m2[7], DELTA);
    assertEquals(m1[8], m2[8], DELTA);
    assertEquals(m1[9], m2[9], DELTA);
    assertEquals(m1[10], m2[10], DELTA);
    assertEquals(m1[11], m2[11], DELTA);
    assertEquals(m1[12], m2[12], DELTA);
    assertEquals(m1[13], m2[13], DELTA);
    assertEquals(m1[14], m2[14], DELTA);
    assertEquals(m1[15], m2[15], DELTA);
  }

  public void testMultiplyByIdentity() {
    Matrix4x4 identity = Matrix4x4.createIdentity();
    Matrix4x4 m = new Matrix4x4(new float[] {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
    });
    assertMatEqual(m, Matrix4x4.multiplyMM(identity, m));
    assertMatEqual(m, Matrix4x4.multiplyMM(m, identity));
  }

  public void testMultiplyByScaling() {
    Matrix4x4 m = new Matrix4x4(new float[] {
        1, 2, 3, 0, 5, 6, 7, 0, 9, 10, 11, 0, 0, 0, 0, 0
    });
    Matrix4x4 scaling = Matrix4x4.createScaling(2, 2, 2);
    Matrix4x4 expected = new Matrix4x4(new float[] {
        2, 4, 6, 0, 10, 12, 14, 0, 18, 20, 22, 0, 0, 0, 0, 0
    });
    assertMatEqual(expected, Matrix4x4.multiplyMM(scaling, m));
    assertMatEqual(expected, Matrix4x4.multiplyMM(m, scaling));
  }

  public void testMultiplyByTranslation() {
    Vector3 v = new Vector3(1, 1, 1);
    Matrix4x4 trans = Matrix4x4.createTranslation(1, 2, 3);
    Vector3 expected = new Vector3(2, 3, 4);
    assertVectorsEqual(expected, Matrix4x4.multiplyMV(trans, v));
  }

  public void testRotation3x3ParallelRotationHasNoEffect() {
    Matrix4x4 m = Matrix4x4.createRotation(MathUtil.PI, new Vector3(0, 1, 0));
    Vector3 v = new Vector3(0, 2, 0);

    assertVectorsEqual(v, Matrix4x4.multiplyMV(m, v));
  }

  public void testRotation3x3PerpendicularRotation() {
    Matrix4x4 m = Matrix4x4.createRotation(MathUtil.PI * 0.25f, new Vector3(0, -1, 0));
    Vector3 v = new Vector3(1, 0, 0);
    float oneOverSqrt2 = 1.0f / MathUtil.sqrt(2.0f);

    assertVectorsEqual(new Vector3(oneOverSqrt2, 0, oneOverSqrt2), Matrix4x4.multiplyMV(m, v));
  }

  public void testRotation3x3UnalignedAxis() {
    Vector3 axis = new Vector3(1, 1, 1);
    axis = VectorUtil.normalized(axis);

    int numRotations = 5;
    Matrix4x4 m = Matrix4x4.createRotation(MathUtil.TWO_PI / numRotations, axis);

    Vector3 start = new Vector3(2.34f, 3, -17.6f);
    // float oneOverSqrt2 = 1.0f / MathUtil.sqrt(2.0f);

    Vector3 v = start;
    for (int i = 0; i < 5; i++) {
      v = Matrix4x4.multiplyMV(m, v);
    }

    assertVectorsEqual(start, v);
  }
}
