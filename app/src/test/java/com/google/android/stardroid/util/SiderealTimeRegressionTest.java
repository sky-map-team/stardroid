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

package com.google.android.stardroid.util;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import junit.framework.TestCase;

/**
 * Tests based on data from
 * http://www.jgiesen.de/astro/astroJS/siderealClock/
 * http://aa.usno.navy.mil/data/docs/JulianDate.php
 * http://www.csgnetwork.com/siderealjuliantimecalc.html
 */
public class SiderealTimeRegressionTest extends TestCase {
  private static float ANGULAR_TOL = 1e-1f;

  public void testDummy() {
    // TODO: delete this once the other tests are enabled.
    // Attempt to find a time that is zero sidereal time
    System.out.println("Time : Sidereal Time : Julian Day");
    GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.set(2009, 2, 20, 12, 07, 24);
    Date time = calendar.getTime();
    for (int i = 0; i < 100; ++i) {
      double siderealTime = TimeUtil.meanSiderealTime(time, 0);
      double julianDay = TimeUtil.calculateJulianDay(time);
      System.out.println(time + ": " + siderealTime + " : " + julianDay);
      time = new Date(time.getTime() + 1000 * 60 * 10);
    }
  }

  public void testZeroTime() {
    // Local sidereal time should be zero here.
    GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.set(2009, 2, 20, 12, 07, 24);
    Date time = calendar.getTime();
    assertEquals(2454911.00514, TimeUtil.calculateJulianDay(time), 0.0007f);  // accurate to 1 min
    assertEquals(0, TimeUtil.meanSiderealTime(time, 0) % 360, ANGULAR_TOL);
  }

  public void testZeroTimeAt90Longitude() {
    // Local sidereal time should be 90 here.
    GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.set(2009, 2, 20, 12, 07, 24);
    Date time = calendar.getTime();
    assertEquals(90, TimeUtil.meanSiderealTime(time, 90) % 360, ANGULAR_TOL);
  }

  public void testABitMoreInteresting() {
    GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.set(2000, 0, 1, 0, 0, 0);
    Date time = calendar.getTime();
    assertEquals(2451544.5, TimeUtil.calculateJulianDay(time), 0.0007f);  // accurate to 1 min
    // Sidereal time should be 6:39:51
    float expectedTime = (6f + 39f / 60 + 51f / 60 / 60) / 24 * 360;
    assertEquals(expectedTime, TimeUtil.meanSiderealTime(time, 0) % 360, ANGULAR_TOL);
  }
}
