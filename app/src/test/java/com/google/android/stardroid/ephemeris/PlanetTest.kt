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
package com.google.android.stardroid.ephemeris

import com.google.android.stardroid.ephemeris.Planet
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.space.CelestialObject
import com.google.android.stardroid.space.Universe
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlanetTest {
    @Test
    fun testCalcNextRiseSetTime() {
        val universe = Universe()
        val testCal = GregorianCalendar()
        testCal.timeZone = TimeZone.getTimeZone("GMT")

        // Tests from (60, 0), just east of Shetland, all times GMT.
        // At the equinox, sunset should be between 17:00 and 18:59.
        testCal[2010, GregorianCalendar.MARCH, 21, 12, 0] = 0
        var loc = LatLong(60f, 0f)
        var sunset = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET)
        sunset!!.timeZone = TimeZone.getTimeZone("GMT")
        Truth.assertThat(
            17 <= sunset[Calendar.HOUR_OF_DAY] && 18 >= sunset[Calendar.HOUR_OF_DAY]
        ).isTrue()
        Truth.assertThat(sunset[Calendar.DAY_OF_MONTH]).isEqualTo(21)
        // Sunrise should be between 5:00 and 6:59 the following day.
        var sunrise = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE)
        sunrise!!.timeZone = TimeZone.getTimeZone("GMT")
        Truth.assertThat(5 <= sunrise[Calendar.HOUR_OF_DAY] && 6 >= sunrise[Calendar.HOUR_OF_DAY])
            .isTrue()
        Truth.assertThat(sunrise[Calendar.DAY_OF_MONTH]).isEqualTo(22)

        // In midsummer, sunset should be between 20:00 and 21:59.
        testCal[2010, GregorianCalendar.JUNE, 21, 12, 0] = 0
        sunset = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET)
        sunset!!.timeZone = TimeZone.getTimeZone("GMT")
        Truth.assertThat(20 <= sunset[Calendar.HOUR_OF_DAY] && 21 >= sunset[Calendar.HOUR_OF_DAY])
            .isTrue()
        Truth.assertThat(sunset[Calendar.DAY_OF_MONTH]).isEqualTo(21)
        // Sunrise should be between 2:00 and 3:59 the following day.
        sunrise = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE)
        sunrise!!.timeZone = TimeZone.getTimeZone("GMT")
        Truth.assertThat(2 <= sunrise[Calendar.HOUR_OF_DAY] && 3 >= sunrise[Calendar.HOUR_OF_DAY])
            .isTrue()
        Truth.assertThat(sunrise[Calendar.DAY_OF_MONTH]).isEqualTo(22)

        // In midwinter, sunset should be between 14:00 and 15:59.
        testCal[2010, GregorianCalendar.DECEMBER, 21, 12, 0] = 0
        sunset = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET)
        sunset!!.timeZone = TimeZone.getTimeZone("GMT")
        Truth.assertThat(14 <= sunset[Calendar.HOUR_OF_DAY] && 15 >= sunset[Calendar.HOUR_OF_DAY])
            .isTrue()
        Truth.assertThat(sunset[Calendar.DAY_OF_MONTH]).isEqualTo(21)
        // Sunrise should be between 8:00 and 9:59 the following day.
        sunrise = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE)
        sunrise!!.timeZone = TimeZone.getTimeZone("GMT")
        Truth.assertThat(8 <= sunrise[Calendar.HOUR_OF_DAY] && 9 >= sunrise[Calendar.HOUR_OF_DAY])
            .isTrue()
        Truth.assertThat(sunrise[Calendar.DAY_OF_MONTH]).isEqualTo(22)

        // Tests from (60, -75), Northern Quebec, all times EST.
        testCal.timeZone = TimeZone.getTimeZone("EST")
        // At the equinox, sunset should be between 17:00 and 18:59.
        testCal[2010, GregorianCalendar.MARCH, 21, 12, 0] = 0
        loc = LatLong(60f, -75f)
        sunset = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET)
        sunset!!.timeZone = TimeZone.getTimeZone("EST")
        Truth.assertThat(17 <= sunset[Calendar.HOUR_OF_DAY] && 18 >= sunset[Calendar.HOUR_OF_DAY])
            .isTrue()
        Truth.assertThat(sunset[Calendar.DAY_OF_MONTH]).isEqualTo(21)
        // Sunrise should be between 5:00 and 6:59 the following day.
        sunrise = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE)
        sunrise!!.timeZone = TimeZone.getTimeZone("EST")
        Truth.assertThat(5 <= sunrise[Calendar.HOUR_OF_DAY] && 6 >= sunrise[Calendar.HOUR_OF_DAY])
            .isTrue()
        Truth.assertThat(sunrise[Calendar.DAY_OF_MONTH]).isEqualTo(22)

        // In midsummer, sunset should be between 20:00 and 21:59.
        testCal[2010, GregorianCalendar.JUNE, 21, 12, 0] = 0
        sunset = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET)
        sunset!!.timeZone = TimeZone.getTimeZone("EST")
        Truth.assertThat(20 <= sunset[Calendar.HOUR_OF_DAY] && 21 >= sunset[Calendar.HOUR_OF_DAY])
            .isTrue()
        Truth.assertThat(sunset[Calendar.DAY_OF_MONTH]).isEqualTo(21)
        // Sunrise should be between 2:00 and 3:59 the following day.
        sunrise = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE)
        sunrise!!.timeZone = TimeZone.getTimeZone("EST")
        Truth.assertThat(2 <= sunrise[Calendar.HOUR_OF_DAY] && 3 >= sunrise[Calendar.HOUR_OF_DAY])
            .isTrue()
        Truth.assertThat(sunrise[Calendar.DAY_OF_MONTH]).isEqualTo(22)

        // In midwinter, sunset should be between 14:00 and 15:59.
        testCal[2010, GregorianCalendar.DECEMBER, 21, 12, 0] = 0
        sunset = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.SET)
        sunset!!.timeZone = TimeZone.getTimeZone("EST")
        Truth.assertThat(14 <= sunset[Calendar.HOUR_OF_DAY] && 15 >= sunset[Calendar.HOUR_OF_DAY])
            .isTrue()
        Truth.assertThat(sunset[Calendar.DAY_OF_MONTH]).isEqualTo(21)
        // Sunrise should be between 8:00 and 9:59 the following day.
        sunrise = universe.solarSystemObjectFor(Planet.Sun)
            .calcNextRiseSetTime(testCal, loc, CelestialObject.RiseSetIndicator.RISE)
        sunrise!!.timeZone = TimeZone.getTimeZone("EST")
        Truth.assertThat(8 <= sunrise[Calendar.HOUR_OF_DAY] && 9 >= sunrise[Calendar.HOUR_OF_DAY])
            .isTrue()
        Truth.assertThat(sunrise[Calendar.DAY_OF_MONTH]).isEqualTo(22)
    }
}