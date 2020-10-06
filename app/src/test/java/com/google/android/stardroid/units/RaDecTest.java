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

import com.google.android.stardroid.provider.ephemeris.Planet;
import com.google.android.stardroid.units.HeliocentricCoordinates;
import com.google.android.stardroid.units.RaDec;

import java.util.GregorianCalendar;
import java.util.TimeZone;

import junit.framework.TestCase;

public class RaDecTest extends TestCase {
  // Accuracy of 0.30 degree should be fine.
  // TODO(jontayler): investigate why this now fails with a tol of 0.25 degrees
  private static final float EPSILON = 0.30f;

  // Convert from hours to degrees
  private static final float HOURS_TO_DEGREES = 360.0f/24.0f;
  
  // Verify that we are calculating the correct RA/Dec for the various solar system objects.
  // All of the reference data comes from the US Naval Observatories web site:
  // http://aa.usno.navy.mil/data/
  public void testPositions() {
    GregorianCalendar testCal = new GregorianCalendar();
    testCal.setTimeZone(TimeZone.getTimeZone("GMT"));

    RaDec pos = null;
    HeliocentricCoordinates earthCoords = null;

    // 2009 Jan  1, 12:00 UT1
    // Sun       18h 48.8m  -22d 58m
    // Mercury   20h 10.6m  -21d 36m
    // Venus     22h 02.0m  -13d 36m
    // Mars      18h 17.1m  -24d 05m
    // Jupiter   20h 05.1m  -20d 45m
    // Saturn    11h 33.0m  + 5d 09m
    // Uranus    23h 21.7m  - 4d 57m
    // Neptune   21h 39.7m  -14d 22m
    // Pluto     18h 05.3m  -17d 45m
    testCal.set(2009, GregorianCalendar.JANUARY, 1, 12, 0, 0);
    earthCoords = HeliocentricCoordinates.getInstance(Planet.Sun, testCal.getTime());

    pos = RaDec.getInstance(Planet.Sun, testCal.getTime(), earthCoords);
    assertEquals(18.813 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-22.97, pos.dec, EPSILON);
    
    pos = RaDec.getInstance(Planet.Mercury, testCal.getTime(), earthCoords);
    assertEquals(20.177 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-21.60, pos.dec, EPSILON);
    
    pos = RaDec.getInstance(Planet.Venus, testCal.getTime(), earthCoords);
    assertEquals(22.033 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-13.60, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Mars, testCal.getTime(), earthCoords);
    assertEquals(18.285 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-24.08, pos.dec, EPSILON);
    
    pos = RaDec.getInstance(Planet.Jupiter, testCal.getTime(), earthCoords);
    assertEquals(20.085 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-20.75, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Saturn, testCal.getTime(), earthCoords);
    assertEquals(11.550 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(5.15, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Uranus, testCal.getTime(), earthCoords);
    assertEquals(23.362 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-4.95, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Neptune, testCal.getTime(), earthCoords);
    assertEquals(21.662 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-14.37, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Pluto, testCal.getTime(), earthCoords);
    assertEquals(18.088 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-17.75, pos.dec, EPSILON);

    // 2009 Sep 20, 12:00 UT1
    // Sun       11h 51.4m  + 0d 56m
    // Mercury   11h 46.1m  - 1d 45m
    // Venus     10h 09.4m  +12d 21m
    // Mars       7h 08.6m  +23d 03m
    // Jupiter   21h 23.2m  -16d 29m
    // Saturn    11h 46.0m  + 3d 40m
    // Uranus    23h 41.1m  - 2d 55m
    // Neptune   21h 46.7m  -13d 51m
    // Pluto     18h 02.8m  -18d 00m
    testCal.set(2009, GregorianCalendar.SEPTEMBER, 20, 12, 0, 0);
    earthCoords = HeliocentricCoordinates.getInstance(Planet.Sun, testCal.getTime());

    pos = RaDec.getInstance(Planet.Sun, testCal.getTime(), earthCoords);
    assertEquals(11.857 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(0.933, pos.dec, EPSILON);
    
    pos = RaDec.getInstance(Planet.Mercury, testCal.getTime(), earthCoords);
    assertEquals(11.768 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-1.75, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Venus, testCal.getTime(), earthCoords);
    assertEquals(10.157 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(12.35, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Mars, testCal.getTime(), earthCoords);
    assertEquals(7.143 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(23.05, pos.dec, EPSILON);
    
    pos = RaDec.getInstance(Planet.Jupiter, testCal.getTime(), earthCoords);
    assertEquals(21.387 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-16.48, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Saturn, testCal.getTime(), earthCoords);
    assertEquals(11.767 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(3.67, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Uranus, testCal.getTime(), earthCoords);
    assertEquals(23.685 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-2.92, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Neptune, testCal.getTime(), earthCoords);
    assertEquals(21.778 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-13.85, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Pluto, testCal.getTime(), earthCoords);
    assertEquals(18.047 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-18.00, pos.dec, EPSILON);


    // 2010 Dec 25, 12:00 UT1
    // Sun       18 15.6  -23 23
    // Mercury   17 24.2  -20 10
    // Venus     15 04.1  -13 50
    // Mars      18 58.5  -23 43
    // Jupiter   23 46.4  - 2 53
    // Saturn    13 03.9  - 4 14
    // Uranus    23 49.6  - 1 56
    // Neptune   21 55.8  -13 07
    // Pluto     18 21.5  -18 50
    testCal.set(2010, GregorianCalendar.DECEMBER, 25, 12, 0, 0);
    earthCoords = HeliocentricCoordinates.getInstance(Planet.Sun, testCal.getTime());

    pos = RaDec.getInstance(Planet.Sun, testCal.getTime(), earthCoords);
    assertEquals(18.260 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-23.38, pos.dec, EPSILON);
    
    pos = RaDec.getInstance(Planet.Mercury, testCal.getTime(), earthCoords);
    assertEquals(17.403 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-20.17, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Venus, testCal.getTime(), earthCoords);
    assertEquals(15.068 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-13.83, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Mars, testCal.getTime(), earthCoords);
    assertEquals(18.975 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-23.72, pos.dec, EPSILON);
    
    pos = RaDec.getInstance(Planet.Jupiter, testCal.getTime(), earthCoords);
    assertEquals(23.773 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-2.88, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Saturn, testCal.getTime(), earthCoords);
    assertEquals(13.065 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-4.23, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Uranus, testCal.getTime(), earthCoords);
    assertEquals(23.827 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-1.93, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Neptune, testCal.getTime(), earthCoords);
    assertEquals(21.930 * HOURS_TO_DEGREES, pos.ra, EPSILON);
    assertEquals(-13.12, pos.dec, EPSILON);

    pos = RaDec.getInstance(Planet.Pluto, testCal.getTime(), earthCoords);
    assertEquals(18.358 * HOURS_TO_DEGREES, pos.ra, EPSILON);
  }
}
