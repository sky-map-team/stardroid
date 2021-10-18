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

package com.google.android.stardroid.math;

import junit.framework.TestCase;

public class GeometryTest extends TestCase {
  private final double TOL = 0.00001;

  public void assertVectorsEqual(Vector3 v, Vector3 w) {
    assertEquals(v.x, w.x, TOL);
    assertEquals(v.y, w.y, TOL);
    assertEquals(v.z, w.z, TOL);
  }

  private void assertMatrixSame(Matrix3x3 m1, Matrix3x3 m2, double tol) {
    assertEquals(m1.xx, m2.xx, tol);
    assertEquals(m1.xy, m2.xy, tol);
    assertEquals(m1.xz, m2.xz, tol);
    assertEquals(m1.yx, m2.yx, tol);
    assertEquals(m1.yy, m2.yy, tol);
    assertEquals(m1.yz, m2.yz, tol);
    assertEquals(m1.zx, m2.zx, tol);
    assertEquals(m1.zy, m2.zy, tol);
    assertEquals(m1.zz, m2.zz, tol);
  }

  private float[][] allTestValues = { {0, 0, 1, 0, 0},
                                      {90, 0, 0, 1, 0},
                                      {0, 90, 0, 0, 1},
                                      {180, 0, -1, 0, 0},
                                      {0, -90, 0, 0, -1},
                                      {270, 0, 0, -1, 0} };

  public void testSphericalToCartesians() {
    for (float[] testValues : allTestValues) {
      float ra = testValues[0];
      float dec = testValues[1];
      float x = testValues[2];
      float y = testValues[3];
      float z = testValues[4];
      Vector3 result = Geometry.getXYZ(new RaDec(ra, dec));
      assertEquals(x, result.x, TOL);
      assertEquals(y, result.y, TOL);
      assertEquals(z, result.z, TOL);
    }
  }

  public void testCartesiansToSphericals() {
    for (float[] testValues : allTestValues) {
      float ra = testValues[0];
      float dec = testValues[1];
      float x = testValues[2];
      float y = testValues[3];
      float z = testValues[4];
      RaDec result =
          RaDec.fromGeocentricCoords(new Vector3(x, y, z));
      assertEquals(ra, result.getRa(), TOL);
      assertEquals(dec, result.getDec(), TOL);
    }
  }

  public void testVectorProduct() {
    // Check that z is x X y
    Vector3 x = new Vector3(1, 0, 0);
    Vector3 y = new Vector3(0, 1, 0);
    Vector3 z = Geometry.vectorProduct(x, y);
    assertVectorsEqual(z, new Vector3(0, 0, 1));

    // Check that a X b is perpendicular to a and b
    Vector3 a = new Vector3(1, -2, 3);
    Vector3 b = new Vector3(2, 0, -4);
    Vector3 c = Geometry.vectorProduct(a, b);
    double aDotc = Geometry.scalarProduct(a, c);
    double bDotc = Geometry.scalarProduct(b, c);
    assertEquals(0, aDotc, TOL);
    assertEquals(0, bDotc, TOL);

    // Check that |a X b| is correct
    Vector3 v= new Vector3(1, 2, 0);
    Vector3 ww = Geometry.vectorProduct(x, v);
    float wwDotww = Geometry.scalarProduct(ww, ww);
    assertEquals(Math.pow(1f * Math.sqrt(5) * Math.sin(Math.atan(2)),2),
        wwDotww, TOL);
  }

  public void testMatrixInversion() {
    Matrix3x3 m = new Matrix3x3(1, 2, 0, 0, 1, 5, 0, 0, 1);
    System.out.println(GeometryTest.formatMatrix(m));
    Matrix3x3 inv = m.getInverse();
    System.out.println(GeometryTest.formatMatrix(inv));
    Matrix3x3 product = Geometry.matrixMultiply(m, inv);
    System.out.println(GeometryTest.formatMatrix(product));
  }

  public void testCalculateRotationMatrix() {
    Matrix3x3 noRotation = Geometry.calculateRotationMatrix(0, new Vector3(1, 2, 3));
    Matrix3x3 identity = new Matrix3x3(1, 0, 0, 0, 1, 0, 0, 0, 1);
    assertMatrixSame(identity, noRotation, TOL);

    Matrix3x3 rotAboutZ = Geometry.calculateRotationMatrix(90, new Vector3(0, 0, 1));
    assertMatrixSame(new Matrix3x3(0, 1, 0, -1, 0, 0, 0, 0, 1), rotAboutZ, TOL);

    Vector3 axis = new Vector3(2, -4, 1);
    axis.normalize();
    Matrix3x3 rotA = Geometry.calculateRotationMatrix(30, axis);
    Matrix3x3 rotB = Geometry.calculateRotationMatrix(-30, axis);

    Matrix3x3 shouldBeIdentity = Geometry.matrixMultiply(rotA, rotB);
    assertMatrixSame(identity, shouldBeIdentity, TOL);

    Vector3 axisPerpendicular = new Vector3(4, 2, 0);
    Vector3 rotatedAxisPerpendicular = Geometry.matrixVectorMultiply(rotA, axisPerpendicular);

    // Should still be perpendicular
    assertEquals(0, Geometry.scalarProduct(axis, rotatedAxisPerpendicular), TOL);
    // And the angle between them should be 30 degrees
    axisPerpendicular.normalize();
    rotatedAxisPerpendicular.normalize();
    assertEquals(Math.cos(30.0 * Geometry.DEGREES_TO_RADIANS),
                 Geometry.scalarProduct(axisPerpendicular, rotatedAxisPerpendicular),
                 TOL);
  }

  public void testMatrixMultiply() {
    Matrix3x3 m1 = new Matrix3x3(1, 2, 4, -1, -3, 5, 3, 2, 6);
    Matrix3x3 m2 = new Matrix3x3(3, -1, 4, 0, 2, 1, 2, -1, 2);
    Vector3 v1 = new Vector3(0, -1, 2);
    Vector3 v2 = new Vector3(2, -2, 3);

    Matrix3x3Test.assertMatricesEqual(
        new Matrix3x3(11, -1, 14, 7, -10, 3, 21, -5, 26),
        Geometry.matrixMultiply(m1, m2), (float) TOL);

    assertVectorsEqual(new Vector3(6, 13, 10),
          Geometry.matrixVectorMultiply(m1, v1));
    assertVectorsEqual(new Vector3(10, 19, 20),
          Geometry.matrixVectorMultiply(m1, v2));
  }

  public static String formatMatrix(Matrix3x3 m) {
    return m.xx + " " + m.xy + " " + m.xz + " "
      + m.yx + " " + m.yy + " " + m.yz + " "
      + m.zx + " " + m.zy + " " + m.zz;
  }
}
