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

import com.google.android.stardroid.units.GeocentricCoordinates;
import com.google.android.stardroid.units.LatLong;
import com.google.android.stardroid.units.Vector3;
import com.google.android.stardroid.util.Geometry;
import com.google.android.stardroid.util.MathUtil;

import java.util.GregorianCalendar;
import java.util.TimeZone;

import junit.framework.TestCase;

/**
 * Test of the observer's model when using magnetic variation.
 *
 * @author John Taylor
 */
// TODO(johntaylor): combine this with AstronomerModelWithMagneticVariationTest
// as there's currently too much code duplication.
public class AstronomerModelWithMagneticVariationTest extends TestCase {
  private static class MagneticDeclinationCalculation implements MagneticDeclinationCalculator {
    private float angle;

    public MagneticDeclinationCalculation(float angle) {
      this.angle = angle;
    }
    public float getDeclination() {
      return angle;
    }

    public void setLocationAndTime(LatLong location, long timeInMillis) {
      // Do nothing
    }
  }

  private final static float TOL_ANGLE = (float) 1e-3;
  private final static float TOL_LENGTH = (float) 1e-3;
  private final static float SQRT2 = MathUtil.sqrt(2f);

  private static void assertVectorEquals(Vector3 v1, Vector3 v2, float tol_angle,
      float tol_length) {
    float normv1 = v1.length();
    float normv2 = v2.length();
    assertEquals("Vectors of different lengths", normv1, normv2, tol_length);
    float cosineSim = Geometry.cosineSimilarity(v1, v2);
    float cosTol = MathUtil.cos(tol_angle);
    assertTrue("Vectors in different directions", cosineSim >= cosTol);
    //TODO look at iteration in Julian day
  }

  public void testAssertVectorEquals() {
    Vector3 v1 = new Vector3(0, 0, 1);
    Vector3 v2 = new Vector3(0, 0, 1);
    assertVectorEquals(v1, v2, 0.0001f, 0.0001f);
    
    v2 = new Vector3(0, 0, 1.1f);
    boolean failed = false;
    try {
      assertVectorEquals(v1, v2, 0.0001f, 0.0001f);
    } catch (junit.framework.AssertionFailedError e) {
      failed = true;
    }
    assertTrue(failed);
    
    v2 = new Vector3(0, 1f, 0);
    failed = false;
    try {
      assertVectorEquals(v1, v2, 0.0001f, 0.0001f);
    } catch (junit.framework.AssertionFailedError e) {
      failed = true;
    }
    assertTrue(failed);
  }

  public void testFlatOnEquatorMag0Degrees() {
    LatLong location = new LatLong(0, 0);
    // The following vectors are in the phone's coordinate system.
    // Phone flat on back, top edge towards North
    final Vector3 acceleration = new Vector3(0, 0, -10);
    // Magnetic field coming in from N
    final Vector3 magneticField = new Vector3(0, -5, -10);

    // These vectors are in celestial coordinates.
    final Vector3 expectedZenith = new Vector3(1, 0, 0);
    final Vector3 expectedNadir = new Vector3(-1, 0, 0);
    final Vector3 expectedNorth = new Vector3(0, 0, 1);
    final Vector3 expectedEast = new Vector3(0, 1, 0);
    final Vector3 expectedSouth = new Vector3(0, 0, -1);
    final Vector3 expectedWest = new Vector3(0, -1, 0);
    final Vector3 expectedPointing = expectedNadir;
    final Vector3 expectedUpAlongPhone = expectedNorth;
    checkPointing(0.0f, location, acceleration, magneticField, expectedZenith, expectedNadir,
        expectedNorth, expectedEast, expectedSouth, expectedWest, expectedPointing,
        expectedUpAlongPhone);
  }

  public void testFlatOnEquatorMagN45DegreesW() {
    LatLong location = new LatLong(0, 0);
    // The following vectors are in the phone's coordinate system.
    // Phone flat on back, top edge towards North
    final Vector3 acceleration = new Vector3(0, 0, -10);
    // Magnetic field coming in from NW
    final Vector3 magneticField = new Vector3(1, -1, -10);

    // These vectors are in celestial coordinates.
    final Vector3 expectedZenith = new Vector3(1, 0, 0);
    final Vector3 expectedNadir = new Vector3(-1, 0, 0);
    final Vector3 expectedNorth = new Vector3(0, 0, 1);
    final Vector3 expectedEast = new Vector3(0, 1, 0);
    final Vector3 expectedSouth = new Vector3(0, 0, -1);
    final Vector3 expectedWest = new Vector3(0, -1, 0);
    final Vector3 expectedPointing = expectedNadir;
    final Vector3 expectedUpAlongPhone = expectedNorth;
    checkPointing(-45.0f, location, acceleration, magneticField, expectedZenith, expectedNadir,
        expectedNorth, expectedEast, expectedSouth, expectedWest, expectedPointing,
        expectedUpAlongPhone);
  }

  public void testStandingUpOnEquatorMagN10DegreesEast() {
    LatLong location = new LatLong(0, 0);
    final Vector3 acceleration = new Vector3(0, -10, 0);
    final Vector3 magneticField = new Vector3(-MathUtil.sin(radians(10)),
                                              10,
                                              MathUtil.cos(radians(10)));
    final Vector3 expectedZenith = new Vector3(1, 0, 0);
    final Vector3 expectedNadir = new Vector3(-1, 0, 0);
    final Vector3 expectedNorth = new Vector3(0, 0, 1);
    final Vector3 expectedEast = new Vector3(0, 1, 0);
    final Vector3 expectedSouth = new Vector3(0, 0, -1);
    final Vector3 expectedWest = new Vector3(0, -1, 0);
    final Vector3 expectedPointing = expectedNorth;
    final Vector3 expectedUpAlongPhone = expectedZenith;
    checkPointing(10, location, acceleration, magneticField, expectedZenith, expectedNadir,
        expectedNorth, expectedEast, expectedSouth, expectedWest, expectedPointing,
        expectedUpAlongPhone);
  }

  /**
   * Convert from degrees to radians.
   */
  private static float radians(float degrees) {
    return degrees * Geometry.DEGREES_TO_RADIANS;
  }

  private void checkPointing(float magDeclination,
                             LatLong location,
                             final Vector3 acceleration,
                             final Vector3 magneticField,
                             final Vector3 expectedZenith,
                             final Vector3 expectedNadir,
                             final Vector3 expectedNorth,
                             final Vector3 expectedEast,
                             final Vector3 expectedSouth,
                             final Vector3 expectedWest,
                             final Vector3 expectedPointing,
                             final Vector3 expectedUpAlongPhone) {
    AstronomerModel astronomer = new AstronomerModelImpl(
        new MagneticDeclinationCalculation(magDeclination));
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
    GeocentricCoordinates pointing = astronomer.getPointing().getLineOfSight();
    GeocentricCoordinates upAlongPhone = astronomer.getPointing().getPerpendicular();
    GeocentricCoordinates north = astronomer.getNorth();
    GeocentricCoordinates east = astronomer.getEast();
    GeocentricCoordinates south = astronomer.getSouth();
    GeocentricCoordinates west = astronomer.getWest();
    GeocentricCoordinates zenith = astronomer.getZenith();
    GeocentricCoordinates nadir = astronomer.getNadir();
    assertVectorEquals(expectedZenith, zenith, TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedNadir, nadir, TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedNorth, north, TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedEast, east, TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedSouth, south, TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedWest, west, TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedPointing, pointing, TOL_LENGTH, TOL_ANGLE);
    assertVectorEquals(expectedUpAlongPhone, upAlongPhone, TOL_LENGTH, TOL_ANGLE);
  }

  public void testFlatLat45Long0MagN180Degrees() {
    LatLong location = new LatLong(45, 0);
    final Vector3 acceleration = new Vector3(0, 0, -10);
    final Vector3 magneticField = new Vector3(0, 10, 0);
    final Vector3 expectedZenith = new Vector3(1 / SQRT2, 0, 1 / SQRT2);
    final Vector3 expectedNadir = new Vector3(-1 / SQRT2, 0, -1 / SQRT2);
    final Vector3 expectedNorth = new Vector3(-1 / SQRT2, 0, 1 / SQRT2);
    final Vector3 expectedEast = new Vector3(0, 1, 0);
    final Vector3 expectedSouth = new Vector3(1 / SQRT2, 0, -1 / SQRT2);
    final Vector3 expectedWest = new Vector3(0, -1, 0);
    final Vector3 expectedPointing = expectedNadir;
    final Vector3 expectedUpAlongPhone = expectedNorth;
    checkPointing(180, location, acceleration, magneticField, expectedZenith, expectedNadir,
        expectedNorth, expectedEast, expectedSouth, expectedWest, expectedPointing,
        expectedUpAlongPhone);
  }
}
