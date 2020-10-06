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

package com.google.android.stardroid.control;

import com.google.android.stardroid.units.LatLong;
import com.google.android.stardroid.units.Vector3;
import com.google.android.stardroid.util.Geometry;
import com.google.android.stardroid.util.MathUtil;

import java.util.GregorianCalendar;
import java.util.TimeZone;

import junit.framework.TestCase;

/**
 * Test of the {@link AstronomerModelImpl} class.
 *
 * @author John Taylor
 */
public class AstronomerModelTest extends TestCase {
  private static final float SQRT2 = MathUtil.sqrt(2f);
  private static final float TOL_ANGLE = 1e-3f;
  private static final float TOL_LENGTH = 1e-3f;

  private AstronomerModel astronomer;

  private static void assertVectorEquals(Vector3 v1, Vector3 v2, float tol_angle,
      float tol_length) {
    float normv1 = v1.length();
    float normv2 = v2.length();
    assertEquals("Vectors of different lengths", normv1, normv2, tol_length);
    float cosineSim = Geometry.cosineSimilarity(v1, v2);
    float cosTol = MathUtil.cos(tol_angle);
    assertTrue("Vectors in different directions", cosineSim >= cosTol);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // For now only test a model with no magnetic correction.
    astronomer = new AstronomerModelImpl(new ZeroMagneticDeclinationCalculator());
  }

  /**
   * Checks that our assertion method works as intended.
   */
  public void testAssertVectorEquals_sameVector() {
    Vector3 v1 = new Vector3(0, 0, 1);
    Vector3 v2 = new Vector3(0, 0, 1);
    assertVectorEquals(v1, v2, 0.0001f, 0.0001f);
  }

  /**
   * Checks that our assertion method works as intended.
   */
  public void testAssertVectorEquals_differentLengths() {
    Vector3 v1 = new Vector3(0, 0, 1.0f);
    Vector3 v2 = new Vector3(0, 0, 1.1f);
    try {
      assertVectorEquals(v1, v2, 0.0001f, 0.0001f);
      fail("Vectors should have been found to have different lengths.");
    } catch (junit.framework.AssertionFailedError e) {
      // Expected.
    }
  }

  /**
   * Checks that our assertion method works as intended.
   */
  public void testAssertVectorEquals_differentDirections() {
    Vector3 v1 = new Vector3(0, 0, 1);
    Vector3 v2 = new Vector3(0, 1, 0);
    try {
      assertVectorEquals(v1, v2, 0.0001f, 0.0001f);
      fail("Vectors should have been found to point in different directions.");
    } catch (junit.framework.AssertionFailedError e) {
      // Expected.
    }
  }

  /**
   * The phone is flat, long side pointing North at lat,long = 0, 90.
   */
  public void testSetPhoneSensorValues_phoneFlatAtLat0Long90() {
    LatLong location = new LatLong(0, 90);
    // Phone flat on back, top edge towards North
    // The following are in the phone's coordinate system.
    Vector3 acceleration = new Vector3(0, 0, -10);
    Vector3 magneticField = new Vector3(0, -1, 10);
    // The following are in the celestial coordinate system.
    Vector3 expectedZenith = new Vector3(0, 1, 0);
    Vector3 expectedNadir = new Vector3(0, -1, 0);
    Vector3 expectedNorth = new Vector3(0, 0, 1);
    Vector3 expectedEast = new Vector3(-1, 0, 0);
    Vector3 expectedSouth = new Vector3(0, 0, -1);
    Vector3 expectedWest = new Vector3(1, 0, 0);
    Vector3 expectedPointing = expectedNadir;
    Vector3 expectedUpAlongPhone = expectedNorth;
    checkModelOrientation(location, acceleration, magneticField, expectedZenith, expectedNadir,
         expectedNorth, expectedEast, expectedSouth, expectedWest, expectedPointing,
         expectedUpAlongPhone);
  }

  /**
   * As previous test, but at lat, long = (45, 0)
   */
  public void testSetPhoneSensorValues_phoneFlatAtLat45Long0() {
    LatLong location = new LatLong(45, 0);
    Vector3 acceleration = new Vector3(0, 0, -10);
    Vector3 magneticField = new Vector3(0, -10, 0);
    Vector3 expectedZenith = new Vector3(1 / SQRT2, 0, 1 / SQRT2);
    Vector3 expectedNadir = new Vector3(-1 / SQRT2, 0, -1 / SQRT2);
    Vector3 expectedNorth = new Vector3(-1 / SQRT2, 0, 1 / SQRT2);
    Vector3 expectedEast = new Vector3(0, 1, 0);
    Vector3 expectedSouth = new Vector3(1 / SQRT2, 0, -1 / SQRT2);
    Vector3 expectedWest = new Vector3(0, -1, 0);
    Vector3 expectedPointing = expectedNadir;
    Vector3 expectedUpAlongPhone = expectedNorth;
    checkModelOrientation(location, acceleration, magneticField, expectedZenith, expectedNadir,
        expectedNorth, expectedEast, expectedSouth, expectedWest, expectedPointing,
        expectedUpAlongPhone);
  }

  /**
   * As previous test, but at lat, long = (0, 0)
   */
  public void testSetPhoneSensorValues_phoneFlatOnEquatorAtMeridian() {
    LatLong location = new LatLong(0, 0);
    // Phone flat on back, top edge towards North
    Vector3 acceleration = new Vector3(0, 0, -10);
    Vector3 magneticField = new Vector3(0, -1, 10);
    Vector3 expectedZenith = new Vector3(1, 0, 0);
    Vector3 expectedNadir = new Vector3(-1, 0, 0);
    Vector3 expectedNorth = new Vector3(0, 0, 1);
    Vector3 expectedEast = new Vector3(0, 1, 0);
    Vector3 expectedSouth = new Vector3(0, 0, -1);
    Vector3 expectedWest = new Vector3(0, -1, 0);
    Vector3 expectedPointing = expectedNadir;
    Vector3 expectedUpAlongPhone = expectedNorth;
    checkModelOrientation(location, acceleration, magneticField, expectedZenith, expectedNadir,
        expectedNorth, expectedEast, expectedSouth, expectedWest, expectedPointing,
        expectedUpAlongPhone);
  }

  /**
   * As previous test, but with the phone vertical, but in landscape mode
   * and pointing east.
   */
  public void testSetPhoneSensorValues_phoneLandscapeFacingEastOnEquatorAtMeridian() {
    LatLong location = new LatLong(0, 0);
    Vector3 acceleration = new Vector3(10, 0, 0);
    Vector3 magneticField = new Vector3(-10, 1, 0);
    Vector3 expectedZenith = new Vector3(1, 0, 0);
    Vector3 expectedNadir = new Vector3(-1, 0, 0);
    Vector3 expectedNorth = new Vector3(0, 0, 1);
    Vector3 expectedEast = new Vector3(0, 1, 0);
    Vector3 expectedSouth = new Vector3(0, 0, -1);
    Vector3 expectedWest = new Vector3(0, -1, 0);
    Vector3 expectedPointing = expectedEast;
    Vector3 expectedUpAlongPhone = expectedSouth;
    checkModelOrientation(location, acceleration, magneticField, expectedZenith, expectedNadir,
        expectedNorth, expectedEast, expectedSouth, expectedWest, expectedPointing,
        expectedUpAlongPhone);
  }

  /**
   * As previous test, but in portrait mode facing north.
   */
  public void testSetPhoneSensorValues_phoneStandingUpFacingNorthOnEquatorAtMeridian() {
    LatLong location = new LatLong(0, 0);
    Vector3 acceleration = new Vector3(0, -10, 0);
    Vector3 magneticField = new Vector3(0, 10, 1);
    Vector3 expectedZenith = new Vector3(1, 0, 0);
    Vector3 expectedNadir = new Vector3(-1, 0, 0);
    Vector3 expectedNorth = new Vector3(0, 0, 1);
    Vector3 expectedEast = new Vector3(0, 1, 0);
    Vector3 expectedSouth = new Vector3(0, 0, -1);
    Vector3 expectedWest = new Vector3(0, -1, 0);
    Vector3 expectedPointing = expectedNorth;
    Vector3 expectedUpAlongPhone = expectedZenith;
    checkModelOrientation(location, acceleration, magneticField, expectedZenith, expectedNadir,
        expectedNorth, expectedEast, expectedSouth, expectedWest, expectedPointing,
        expectedUpAlongPhone);
  }

  private void checkModelOrientation(
      LatLong location, Vector3 acceleration, Vector3 magneticField, Vector3 expectedZenith,
      Vector3 expectedNadir, Vector3 expectedNorth, Vector3 expectedEast, Vector3 expectedSouth,
      Vector3 expectedWest, Vector3 expectedPointing, Vector3 expectedUpAlongPhone) {
    astronomer.setLocation(location);

    Clock fakeClock = new Clock() {
      @Override
      public long getTimeInMillisSinceEpoch() {
        // This date is special as RA, DEC = (0, 0) is directly overhead at the
        // equator on the Greenwich meridian.
        // 12:07 March 20th 2009
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.set(2009, 2, 20, 12, 07, 24);
        return calendar.getTimeInMillis();
      }
    };

    astronomer.setClock(fakeClock);
    astronomer.setPhoneSensorValues(acceleration, magneticField);
    assertVectorEquals(expectedZenith, astronomer.getZenith(), TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedNadir, astronomer.getNadir(), TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedNorth, astronomer.getNorth(), TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedEast, astronomer.getEast(), TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedSouth, astronomer.getSouth(), TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedWest, astronomer.getWest(), TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedPointing, astronomer.getPointing().getLineOfSight(),
        TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedUpAlongPhone, astronomer.getPointing().getPerpendicular(),
        TOL_LENGTH, TOL_ANGLE);
  }
}
