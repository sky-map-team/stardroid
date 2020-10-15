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

public class VectorUtilTest extends TestCase {

  private static final float DELTA = 0.00001f;

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

  public void testDotProduct() {
    Vector3 v1 = new Vector3(1, 2, 3);
    Vector3 v2 = new Vector3(0.3f, 0.4f, 0.5f);

    float dp = VectorUtil.dotProduct(v1, v2);

    assertEquals(2.6f, dp, DELTA);
  }
}
