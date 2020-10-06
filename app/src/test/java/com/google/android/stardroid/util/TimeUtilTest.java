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

import junit.framework.TestCase;

import java.util.GregorianCalendar;
import java.util.TimeZone;

public class TimeUtilTest extends TestCase {

  private static final float TOL = 0.0001f;     // Tolerance for Julian Date calculations
  private static final float LMST_TOL = 0.15f;  // Tolerance for LMST calculation
  private static final float HOURS_TO_DEGREES = 360.0f/24.0f;  // Convert from hours to degrees

  public void testJulianDate2000AD() {
    // To begin with, make sure we can get a GregorianCalender object
    // to agree with UNIX time for 2000 epoch.
    long unix2000 = 946684800; // Confirm using 'date -r 946684800'.
    GregorianCalendar year2000 = new GregorianCalendar();
    year2000.setTimeZone(TimeZone.getTimeZone("GMT"));
    // Set to midnight GMT at beginning of 2000-01-01.
    year2000.set(2000, GregorianCalendar.JANUARY, 1, 0, 0, 0);
    // Even when you tell the Calendar to fix on a particular day down
    // to the number of seconds, it still adds the number of milliseconds
    // from your system clock unless you set it to zero separately.
    year2000.set(GregorianCalendar.MILLISECOND, 0);
    // Thanks to the beauty of Object OverOriented design, we run getTime().
    // on the Calendar to get a Date, and getTime() on the date to finally
    // get a long integer.
    assertEquals(unix2000, year2000.getTime().getTime() / 1000);
    // January 1, 2000 at midday corresponds to JD = 2451545.0, according to
    // http://en.wikipedia.org/wiki/Julian_day#Gregorian_calendar_from_Julian_day_number.
    // So midnight before is half a day earlier.
    assertEquals(2451544.5f, TimeUtil.calculateJulianDay(year2000.getTime()), TOL);
  }

  // Make sure that we correctly generate Julian dates. Standard values
  // were obtained from the USNO web site.
  // http://www.usno.navy.mil/USNO/astronomical-applications/data-services/jul-date
  public void testJulianDate() {
    GregorianCalendar testCal = new GregorianCalendar();
    testCal.setTimeZone(TimeZone.getTimeZone("GMT"));

    // Jan 1, 2009, 12:00 UT1 
    testCal.set(2009, GregorianCalendar.JANUARY, 1, 12, 0, 0);
    assertEquals(2454833.0f, TimeUtil.calculateJulianDay(testCal.getTime()), TOL);

    // Jul 4, 2009, 12:00 UT1
    testCal.set(2009, GregorianCalendar.JULY, 4, 12, 0, 0);
    assertEquals(2455017.0f, TimeUtil.calculateJulianDay(testCal.getTime()), TOL);

    // Sep 20, 2009, 12:00 UT1
    testCal.set(2009, GregorianCalendar.SEPTEMBER, 20, 12, 0, 0);
    assertEquals(2455095.0f, TimeUtil.calculateJulianDay(testCal.getTime()), TOL);

    // Dec 25, 2010, 12:00 UT1
    testCal.set(2010, GregorianCalendar.DECEMBER, 25, 12, 0, 0);
    assertEquals(2455556.0f, TimeUtil.calculateJulianDay(testCal.getTime()), TOL);
  }

  // Make sure that we correctly generate Julian dates. Standard values
  // were obtained from the USNO web site.
  // http://www.usno.navy.mil/USNO/astronomical-applications/data-services/jul-date
  public void testJulianCenturies() {
    GregorianCalendar testCal = new GregorianCalendar();
    testCal.setTimeZone(TimeZone.getTimeZone("GMT"));

    // Jan 1, 2009, 12:00 UT1 
    testCal.set(2009, GregorianCalendar.JANUARY, 1, 12, 0, 0);
    assertEquals(0.09002f, TimeUtil.julianCenturies(testCal.getTime()), TOL);

    // Jul 4, 2009, 12:00 UT1
    testCal.set(2009, GregorianCalendar.JULY, 4, 12, 0, 0);
    assertEquals(0.09506f, TimeUtil.julianCenturies(testCal.getTime()), TOL);

    // Sep 20, 2009, 12:00 UT1
    testCal.set(2009, GregorianCalendar.SEPTEMBER, 20, 12, 0, 0);
    assertEquals(0.09719f, TimeUtil.julianCenturies(testCal.getTime()), TOL);

    // Dec 25, 2010, 12:00 UT1
    testCal.set(2010, GregorianCalendar.DECEMBER, 25, 12, 0, 0);
    assertEquals(0.10982f, TimeUtil.julianCenturies(testCal.getTime()), TOL);
  }

  // Verify that we are calculating the correct local mean sidereal time.
  public void testMeanSiderealTime() {
    GregorianCalendar testCal = new GregorianCalendar();
    testCal.setTimeZone(TimeZone.getTimeZone("GMT"));

    // Longitude of selected cities
    float pit =  -79.97f;  // W 79 58'12.0"
    float lon =   -0.13f;  // W 00 07'41.0"
    float tok =  139.77f;  // E139 46'00.0"

    // A couple of select dates:
    // Jan  1, 2009, 12:00 UT1
    testCal.set(2009, GregorianCalendar.JANUARY, 1, 12, 0, 0);
    assertEquals(13.42f * HOURS_TO_DEGREES,
        TimeUtil.meanSiderealTime(testCal.getTime(), pit), LMST_TOL);
    assertEquals(18.74f * HOURS_TO_DEGREES,
        TimeUtil.meanSiderealTime(testCal.getTime(), lon), LMST_TOL);
    assertEquals(4.07f * HOURS_TO_DEGREES,
        TimeUtil.meanSiderealTime(testCal.getTime(), tok), LMST_TOL);

    // Sep 20, 2009, 12:00 UT1
    testCal.set(2009, GregorianCalendar.SEPTEMBER, 20, 12, 0, 0);
    assertEquals(6.64f * HOURS_TO_DEGREES,
        TimeUtil.meanSiderealTime(testCal.getTime(), pit), LMST_TOL);
    assertEquals(11.96f * HOURS_TO_DEGREES,
        TimeUtil.meanSiderealTime(testCal.getTime(), lon), LMST_TOL);
    assertEquals(21.29f * HOURS_TO_DEGREES,
        TimeUtil.meanSiderealTime(testCal.getTime(), tok), LMST_TOL);

    // Dec 25, 2010, 12:00 UT1
    testCal.set(2010, GregorianCalendar.DECEMBER, 25, 12, 0, 0);
    assertEquals(12.92815f * HOURS_TO_DEGREES,
        TimeUtil.meanSiderealTime(testCal.getTime(), pit), LMST_TOL);
    assertEquals(18.25f * HOURS_TO_DEGREES,
        TimeUtil.meanSiderealTime(testCal.getTime(), lon), LMST_TOL);
    assertEquals(3.58f * HOURS_TO_DEGREES,
        TimeUtil.meanSiderealTime(testCal.getTime(), tok), LMST_TOL);
  }
}
