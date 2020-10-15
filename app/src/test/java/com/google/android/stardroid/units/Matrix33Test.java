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

package com.google.android.stardroid.units;

import com.google.android.stardroid.units.Matrix33;
import com.google.android.stardroid.units.Vector3;
import com.google.android.stardroid.util.Geometry;

import junit.framework.TestCase;

public class Matrix33Test extends TestCase {
  private static float TOL = 0.00001f;

  public static void assertMatricesEqual(Matrix33 m1, Matrix33 m2, float TOL) {
    assertEquals(m1.xx, m2.xx, TOL);
    assertEquals(m1.xy, m2.xy, TOL);
    assertEquals(m1.xz, m2.xz, TOL);
    assertEquals(m1.yx, m2.yx, TOL);
    assertEquals(m1.yy, m2.yy, TOL);
    assertEquals(m1.yz, m2.yz, TOL);
    assertEquals(m1.zx, m2.zx, TOL);
    assertEquals(m1.zy, m2.zy, TOL);
    assertEquals(m1.zz, m2.zz, TOL);
  }

  public void testDeterminant() {
    assertEquals(1, Matrix33.getIdMatrix().getDeterminant(), TOL);
  }

  public void testIdInverse() {
    assertMatricesEqual(Matrix33.getIdMatrix(), Matrix33.getIdMatrix().getInverse(), TOL);
  }

  public void testMatrix33Inversion() {
    Matrix33 m = new Matrix33(1, 2, 0, 0, 1, 5, 0, 0, 1);
    Matrix33 inv = m.getInverse();
    Matrix33 product = Geometry.matrixMultiply(m, inv);
    assertMatricesEqual(Matrix33.getIdMatrix(), product, TOL);

    m = new Matrix33(1, 2, 3, 6, 5, 4, 0, 0, 1);
    inv = m.getInverse();
    product = Geometry.matrixMultiply(m, inv);
    assertMatricesEqual(Matrix33.getIdMatrix(), product, TOL);
  }

  public void testTranspose() {
    Matrix33 m = new Matrix33(1, 2, 3, 4, 5, 6, 7, 8, 9);
    m.transpose();
    Matrix33 mt = new Matrix33(1, 4, 7, 2, 5, 8, 3, 6, 9);
    assertMatricesEqual(m, mt, TOL);
  }

  public void testConstructFromColVectors() {
    Vector3 v1 = new Vector3(1, 2, 3);
    Vector3 v2 = new Vector3(4, 5, 6);
    Vector3 v3 = new Vector3(7, 8, 9);
    Matrix33 m = new Matrix33(1, 4, 7,
                              2, 5, 8,
                              3, 6, 9);

    Matrix33 mt = new Matrix33(v1, v2, v3);
    assertMatricesEqual(m, mt, TOL);
  }

  public void testConstructFromRowVectors() {
    Vector3 v1 = new Vector3(1, 2, 3);
    Vector3 v2 = new Vector3(4, 5, 6);
    Vector3 v3 = new Vector3(7, 8, 9);
    Matrix33 m = new Matrix33(1, 4, 7,
                              2, 5, 8,
                              3, 6, 9);
    m.transpose();
    Matrix33 mt = new Matrix33(v1, v2, v3, false);
    assertMatricesEqual(m, mt, TOL);
  }
}
