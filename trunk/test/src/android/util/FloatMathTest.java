// Copyright 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package android.util;

import junit.framework.TestCase;

/**
 * Unit tests for the FloatMath wrapper class.
 *
 * @author Brent Bryan
 */
public class FloatMathTest extends TestCase {
  private static final double tolerance = 1.0e-7;
  
  public void testSqrt() {
    assertEquals(2.0, FloatMath.sqrt(4.0f), tolerance);
    assertEquals(Math.sqrt(17), FloatMath.sqrt(17.0f), tolerance);
  }

  public void testFloor() {
    assertEquals(1.0, FloatMath.floor(1.0f), tolerance);
    assertEquals(1.0, FloatMath.floor(1.9f), tolerance);
  }

  public void testCeil() {
    assertEquals(2.0, FloatMath.ceil(1.1f), tolerance);
    assertEquals(2.0, FloatMath.ceil(2.0f), tolerance);
  }
  
  public void testSin() {
    assertEquals(Math.sin(1.0), FloatMath.sin(1.0f), tolerance);
    assertEquals(Math.sin(0.3), FloatMath.sin(0.3f), tolerance);
  }

  public void testCos() {
    assertEquals(Math.cos(1.2), FloatMath.cos(1.2f), tolerance);
    assertEquals(Math.cos(0.3), FloatMath.cos(0.3f), tolerance);
  }
}
