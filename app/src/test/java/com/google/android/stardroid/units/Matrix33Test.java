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

import com.google.android.stardroid.util.Geometry;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class Matrix33Test {
  private static float TOL = 0.00001f;

  public static void assertMatricesEqual(Matrix33 m1, Matrix33 m2, float TOL) {
    assertThat(m1.xx).isWithin(TOL).of(m2.xx);
    assertThat(m1.xy).isWithin(TOL).of(m2.xy);
    assertThat(m1.xz).isWithin(TOL).of(m2.xz);
    assertThat(m1.yx).isWithin(TOL).of(m2.yx);
    assertThat(m1.yy).isWithin(TOL).of(m2.yy);
    assertThat(m1.yz).isWithin(TOL).of(m2.yz);
    assertThat(m1.zx).isWithin(TOL).of(m2.zx);
    assertThat(m1.zy).isWithin(TOL).of(m2.zy);
    assertThat(m1.zz).isWithin(TOL).of(m2.zz);
  }

  @Test
  public void testDeterminant() {
    assertThat(Matrix33.getIdMatrix().getDeterminant()).isWithin(TOL).of(1f);
  }

  @Test
  public void testIdInverse() {
    assertMatricesEqual(Matrix33.getIdMatrix(), Matrix33.getIdMatrix().getInverse(), TOL);
  }

  @Test
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

  @Test
  public void testTranspose() {
    Matrix33 m = new Matrix33(1, 2, 3, 4, 5, 6, 7, 8, 9);
    m.transpose();
    Matrix33 mt = new Matrix33(1, 4, 7, 2, 5, 8, 3, 6, 9);
    assertMatricesEqual(m, mt, TOL);
  }

  @Test
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

  @Test
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
