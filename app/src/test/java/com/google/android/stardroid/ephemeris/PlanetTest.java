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

package com.google.android.stardroid.ephemeris;

import com.google.android.stardroid.provider.ephemeris.Planet;
import com.google.android.stardroid.units.LatLong;
import com.google.android.stardroid.units.RaDec;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(manifest= Config.NONE)
public class PlanetTest extends TestCase {
  // Accuracy of our position calculations, in degrees.
  private static final float POS_TOL = 0.2f;

  // Accuracy of our Illumination calculations, in percent.
  private static final float PHASE_TOL = 1.0f;

  // Convert from hours to degrees
  private static final float HOURS_TO_DEGREES = 360.0f/24.0f;

  // Verify that we are calculating a valid lunar RA/Dec.
  @Test
  public void testLunarGeocentricLocation() {
    GregorianCalendar testCal = new GregorianCalendar();
    testCal.setTimeZone(TimeZone.getTimeZone("GMT"));

    // 2009 Jan  1, 12:00 UT1: RA = 22h 27m 20.423s, Dec = - 7d  9m 49.94s
    testCal.set(2009, GregorianCalendar.JANUARY, 1, 12, 0, 0);
    RaDec lunarPos = Planet.calculateLunarGeocentricLocation(testCal.getTime());
    assertEquals(22.456 * HOURS_TO_DEGREES, lunarPos.ra, POS_TOL);
    assertEquals(-7.164, lunarPos.dec, POS_TOL);

    // 2009 Sep 20, 12:00 UT1: RA = 13h  7m 23.974s, Dec = -12d 36m  6.15s
    testCal.set(2009, GregorianCalendar.SEPTEMBER, 20, 12, 0, 0);
    lunarPos = Planet.calculateLunarGeocentricLocation(testCal.getTime());
    assertEquals(13.123 * HOURS_TO_DEGREES, lunarPos.ra, POS_TOL);
    assertEquals(-12.602, lunarPos.dec, POS_TOL);

    // 2010 Dec 25, 12:00 UT1: RA =  9h 54m 53.914s, Dec = +8d 3m 22.00s
    testCal.set(2010, GregorianCalendar.DECEMBER, 25, 12, 0, 0);
    lunarPos = Planet.calculateLunarGeocentricLocation(testCal.getTime());
    assertEquals(9.915 * HOURS_TO_DEGREES, lunarPos.ra, POS_TOL);
    assertEquals(8.056, lunarPos.dec, POS_TOL);
  }

  // Verify illumination calculations for bodies that matter (Mercury, Venus, Mars, and Moon)
  // TODO(serafini): please fix and reenable
  // @Test
  public void disableTestIllumination() {
    GregorianCalendar testCal = new GregorianCalendar();
    testCal.setTimeZone(TimeZone.getTimeZone("GMT"));

    // 2009 Jan  1, 12:00 UT1
    testCal.set(2009, GregorianCalendar.JANUARY, 1, 12, 0, 0);
    assertEquals(21.2, Planet.Moon.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);
    assertEquals(69.5, Planet.Mercury.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);
    assertEquals(57.5, Planet.Venus.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);
    assertEquals(99.8, Planet.Mars.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);

    // 2009 Sep 20, 12:00 UT1
    testCal.set(2009, GregorianCalendar.SEPTEMBER, 20, 12, 0, 0);
    assertEquals(4.1, Planet.Moon.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);
    assertEquals(0.5, Planet.Mercury.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);
    assertEquals(88.0, Planet.Venus.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);
    assertEquals(88.7, Planet.Mars.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);

    // 2010 Dec 25, 12:00 UT1
    testCal.set(2010, GregorianCalendar.DECEMBER, 25, 12, 0, 0);
    assertEquals(79.0, Planet.Moon.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);
    assertEquals(12.1, Planet.Mercury.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);
    assertEquals(42.0, Planet.Venus.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);
    assertEquals(99.6, Planet.Mars.calculatePercentIlluminated(testCal.getTime()), PHASE_TOL);
  }

  @Test
  public void testCalcNextRiseSetTime() {
    GregorianCalendar testCal = new GregorianCalendar();
    testCal.setTimeZone(TimeZone.getTimeZone("GMT"));

    // Tests from (60, 0), just east of Shetland, all times GMT.
    // At the equinox, sunset should be between 17:00 and 18:59.
    testCal.set(2010, GregorianCalendar.MARCH, 21, 12, 0, 0);
    LatLong loc = new LatLong(60, 0);
    Calendar sunset = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 17 <= sunset.get(Calendar.HOUR_OF_DAY) && 18 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 5:00 and 6:59 the following day.
    Calendar sunrise = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 5 <= sunrise.get(Calendar.HOUR_OF_DAY) && 6 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));

    // In midsummer, sunset should be between 20:00 and 21:59.
    testCal.set(2010, GregorianCalendar.JUNE, 21, 12, 0, 0);
    sunset = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 20 <= sunset.get(Calendar.HOUR_OF_DAY) && 21 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 2:00 and 3:59 the following day.
    sunrise = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 2 <= sunrise.get(Calendar.HOUR_OF_DAY) && 3 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));

    // In midwinter, sunset should be between 14:00 and 15:59.
    testCal.set(2010, GregorianCalendar.DECEMBER, 21, 12, 0, 0);
    sunset = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 14 <= sunset.get(Calendar.HOUR_OF_DAY) && 15 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 8:00 and 9:59 the following day.
    sunrise = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 8 <= sunrise.get(Calendar.HOUR_OF_DAY) && 9 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));

    // Tests from (60, -75), Northern Quebec, all times EST.
    testCal.setTimeZone(TimeZone.getTimeZone("EST"));
    // At the equinox, sunset should be between 17:00 and 18:59.
    testCal.set(2010, GregorianCalendar.MARCH, 21, 12, 0, 0);
    loc = new LatLong(60, -75);
    sunset = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 17 <= sunset.get(Calendar.HOUR_OF_DAY) && 18 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 5:00 and 6:59 the following day.
    sunrise = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 5 <= sunrise.get(Calendar.HOUR_OF_DAY) && 6 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));

    // In midsummer, sunset should be between 20:00 and 21:59.
    testCal.set(2010, GregorianCalendar.JUNE, 21, 12, 0, 0);
    sunset = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 20 <= sunset.get(Calendar.HOUR_OF_DAY) && 21 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 2:00 and 3:59 the following day.
    sunrise = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 2 <= sunrise.get(Calendar.HOUR_OF_DAY) && 3 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));

    // In midwinter, sunset should be between 14:00 and 15:59.
    testCal.set(2010, GregorianCalendar.DECEMBER, 21, 12, 0, 0);
    sunset = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 14 <= sunset.get(Calendar.HOUR_OF_DAY) && 15 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 8:00 and 9:59 the following day.
    sunrise = Planet.Sun.calcNextRiseSetTime(testCal, loc, Planet.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 8 <= sunrise.get(Calendar.HOUR_OF_DAY) && 9 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));
  }
}
