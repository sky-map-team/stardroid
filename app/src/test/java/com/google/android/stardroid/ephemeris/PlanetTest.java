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

import com.google.android.stardroid.ephemeris.Planet;
import com.google.android.stardroid.math.LatLong;
import com.google.android.stardroid.math.RaDec;
import com.google.android.stardroid.space.CelestialObject;
import com.google.android.stardroid.space.Universe;

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

  @Test
  public void testCalcNextRiseSetTime() {
    Universe universe = new Universe();
    GregorianCalendar testCal = new GregorianCalendar();
    testCal.setTimeZone(TimeZone.getTimeZone("GMT"));

    // Tests from (60, 0), just east of Shetland, all times GMT.
    // At the equinox, sunset should be between 17:00 and 18:59.
    testCal.set(2010, GregorianCalendar.MARCH, 21, 12, 0, 0);
    LatLong loc = new LatLong(60, 0);
    Calendar sunset = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 17 <= sunset.get(Calendar.HOUR_OF_DAY) && 18 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 5:00 and 6:59 the following day.
    Calendar sunrise = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 5 <= sunrise.get(Calendar.HOUR_OF_DAY) && 6 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));

    // In midsummer, sunset should be between 20:00 and 21:59.
    testCal.set(2010, GregorianCalendar.JUNE, 21, 12, 0, 0);
    sunset = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 20 <= sunset.get(Calendar.HOUR_OF_DAY) && 21 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 2:00 and 3:59 the following day.
    sunrise = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 2 <= sunrise.get(Calendar.HOUR_OF_DAY) && 3 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));

    // In midwinter, sunset should be between 14:00 and 15:59.
    testCal.set(2010, GregorianCalendar.DECEMBER, 21, 12, 0, 0);
    sunset = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 14 <= sunset.get(Calendar.HOUR_OF_DAY) && 15 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 8:00 and 9:59 the following day.
    sunrise = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals(true,
                 8 <= sunrise.get(Calendar.HOUR_OF_DAY) && 9 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));

    // Tests from (60, -75), Northern Quebec, all times EST.
    testCal.setTimeZone(TimeZone.getTimeZone("EST"));
    // At the equinox, sunset should be between 17:00 and 18:59.
    testCal.set(2010, GregorianCalendar.MARCH, 21, 12, 0, 0);
    loc = new LatLong(60, -75);
    sunset = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 17 <= sunset.get(Calendar.HOUR_OF_DAY) && 18 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 5:00 and 6:59 the following day.
    sunrise = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 5 <= sunrise.get(Calendar.HOUR_OF_DAY) && 6 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));

    // In midsummer, sunset should be between 20:00 and 21:59.
    testCal.set(2010, GregorianCalendar.JUNE, 21, 12, 0, 0);
    sunset = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 20 <= sunset.get(Calendar.HOUR_OF_DAY) && 21 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 2:00 and 3:59 the following day.
    sunrise = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 2 <= sunrise.get(Calendar.HOUR_OF_DAY) && 3 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));

    // In midwinter, sunset should be between 14:00 and 15:59.
    testCal.set(2010, GregorianCalendar.DECEMBER, 21, 12, 0, 0);
    sunset = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET);
    sunset.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 14 <= sunset.get(Calendar.HOUR_OF_DAY) && 15 >= sunset.get(Calendar.HOUR_OF_DAY));
    assertEquals(21, sunset.get(Calendar.DAY_OF_MONTH));
    // Sunrise should be between 8:00 and 9:59 the following day.
    sunrise = universe.solarSystemObjectFor(Planet.Sun).calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE);
    sunrise.setTimeZone(TimeZone.getTimeZone("EST"));
    assertEquals(true,
                 8 <= sunrise.get(Calendar.HOUR_OF_DAY) && 9 >= sunrise.get(Calendar.HOUR_OF_DAY));
    assertEquals(22, sunrise.get(Calendar.DAY_OF_MONTH));
  }
}
