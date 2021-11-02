package com.google.android.stardroid.space

import com.google.android.stardroid.math.HOURS_TO_DEGREES
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.*

// Accuracy of our position calculations, in degrees.
private const val POS_TOL = 0.2f

class MoonTest {
    // Verify that we are calculating a valid lunar RA/Dec.
    @Test
    fun testLunarGeocentricLocation() {
        val moon = Moon()
        val testCal = GregorianCalendar()
        testCal.setTimeZone(TimeZone.getTimeZone("GMT"))

        // 2009 Jan  1, 12:00 UT1: RA = 22h 27m 20.423s, Dec = - 7d  9m 49.94s
        testCal.set(2009, GregorianCalendar.JANUARY, 1, 12, 0, 0)
        var lunarPos = moon.getRaDec(testCal.getTime())
        assertThat(lunarPos.ra).isWithin(POS_TOL).of(22.456f * HOURS_TO_DEGREES)
        assertThat(lunarPos.dec).isWithin(POS_TOL).of(-7.164f)

        // 2009 Sep 20, 12:00 UT1: RA = 13h  7m 23.974s, Dec = -12d 36m  6.15s
        testCal.set(2009, GregorianCalendar.SEPTEMBER, 20, 12, 0, 0)
        lunarPos = moon.getRaDec(testCal.getTime())
        assertThat(lunarPos.ra).isWithin(POS_TOL).of(13.123f * HOURS_TO_DEGREES)
        assertThat(lunarPos.dec).isWithin(POS_TOL).of(-12.602f)

        // 2010 Dec 25, 12:00 UT1: RA =  9h 54m 53.914s, Dec = +8d 3m 22.00s
        testCal.set(2010, GregorianCalendar.DECEMBER, 25, 12, 0, 0)
        lunarPos = moon.getRaDec(testCal.getTime())
        assertThat(lunarPos.ra).isWithin(POS_TOL).of(9.915f * HOURS_TO_DEGREES)
        assertThat(lunarPos.dec).isWithin(POS_TOL).of(8.056f)
    }
}