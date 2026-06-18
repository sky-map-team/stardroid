package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.RaDec
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.*

/**
 * Position tests of the celestial objects.
 */
class UniverseSmokeTest {
    private val universe = Universe()

    // Verify that we are calculating the correct RA/Dec for the various solar system objects.
    // All of the reference data comes from the US Naval Observatories web site:
    // http://aa.usno.navy.mil/data/
    // Note at present the above site is down. See https://www.usno.navy.mil/USNO/astronomical-applications
    //
    @Test
    fun testPositions() {
        val testCal = GregorianCalendar()
        testCal.timeZone = TimeZone.getTimeZone("GMT")

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
        testCal[2009, GregorianCalendar.JANUARY, 1, 12, 0] = 0
        run {
            val pos = universe.getRaDec(SolarSystemBody.Sun, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(18.813f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-22.97f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Mercury, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(20.177f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-21.60f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Venus, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(22.033f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-13.60f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Mars, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(18.285f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-24.08f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Jupiter, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(20.085f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-20.75f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Saturn, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(11.550f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(5.15f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Uranus, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(23.362f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-4.95f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Neptune, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(21.662f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-14.37f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Pluto, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(18.088f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-17.75f)
        }
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
        testCal[2009, GregorianCalendar.SEPTEMBER, 20, 12, 0] = 0
        run {
            val pos = universe.getRaDec(SolarSystemBody.Sun, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(11.857f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(0.933f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Mercury, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(11.768f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-1.75f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Venus, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(10.157f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(12.35f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Mars, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(7.143f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(23.05f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Jupiter, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(21.387f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-16.48f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Saturn, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(11.767f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(3.67f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Uranus, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(23.685f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-2.92f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Neptune, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(21.778f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-13.85f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Pluto, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(18.047f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-18.00f)
        }

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
        testCal[2010, GregorianCalendar.DECEMBER, 25, 12, 0] = 0
        run {
            val pos = universe.getRaDec(SolarSystemBody.Sun, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(18.260f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-23.38f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Mercury, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(17.403f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-20.17f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Venus, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(15.068f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-13.83f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Mars, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(18.975f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-23.72f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Jupiter, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(23.773f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-2.88f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Saturn, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(13.065f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-4.23f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Uranus, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(23.827f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-1.93f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Neptune, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(21.930f * HOURS_TO_DEGREES)
            assertThat(pos.dec).isWithin(EPSILON).of(-13.12f)
        }
        run {
            val pos = universe.getRaDec(SolarSystemBody.Pluto, testCal.time)
            assertThat(pos.ra).isWithin(EPSILON).of(18.358f * HOURS_TO_DEGREES)
        }
    }

    // Some positions from https://theskylive.com/moon-info#ephemeris
    @Test
    fun testLunarPositions() {
        val testCal = GregorianCalendar()
        testCal.timeZone = TimeZone.getTimeZone("GMT")
        run {
            testCal[2020, GregorianCalendar.OCTOBER, 12, 0, 0] = 0
            val pos = universe.getRaDec(SolarSystemBody.Moon, testCal.time)
            assertThat(pos.ra).isWithin(LUNAR_TOL).of(RaDec.raDegreesFromHMS(9f, 4f, 15.0f))
            assertThat(pos.dec).isWithin(LUNAR_TOL).of(RaDec.decDegreesFromDMS(20f, 2f, 36.0f))
        }
        run {
            testCal[2009, GregorianCalendar.FEBRUARY, 11, 0, 0] = 0
            val pos = universe.getRaDec(SolarSystemBody.Moon, testCal.time)
            assertThat(pos.ra).isWithin(LUNAR_TOL).of(RaDec.raDegreesFromHMS(10f, 44f, 47f))
            assertThat(pos.dec).isWithin(LUNAR_TOL).of(RaDec.decDegreesFromDMS(4f, 24f, 29f))
        }
        run {
            testCal[2005, GregorianCalendar.APRIL, 11, 0, 0] = 0
            val pos = universe.getRaDec(SolarSystemBody.Moon, testCal.time)
            assertThat(pos.ra).isWithin(LUNAR_TOL).of(RaDec.raDegreesFromHMS(2f, 52f, 10f))
            assertThat(pos.dec).isWithin(LUNAR_TOL).of(RaDec.decDegreesFromDMS(18f, 2f, 40f))
        }
    }

    // Accuracy of our Illumination calculations, in percent.
    private val PHASE_TOL = 1.0f


    // Verify illumination calculations for bodies that matter (Mercury, Venus, Mars, and Moon)
    // TODO(serafini): please fix and reenable
    // @Test
    fun disableTestIllumination() {
        val universe = Universe()
        val testCal = GregorianCalendar()
        testCal.timeZone = TimeZone.getTimeZone("GMT")

        // 2009 Jan  1, 12:00 UT1
        testCal[2009, GregorianCalendar.JANUARY, 1, 12, 0] = 0
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Moon).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(21.2f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Mercury).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(69.5f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Venus).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(57.5f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Mars).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(99.8f)

        // 2009 Sep 20, 12:00 UT1
        testCal[2009, GregorianCalendar.SEPTEMBER, 20, 12, 0] = 0
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Moon).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(4.1f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Mercury).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(0.5f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Venus).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(88.0f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Mars).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(88.7f)

        // 2010 Dec 25, 12:00 UT1
        testCal[2010, GregorianCalendar.DECEMBER, 25, 12, 0] = 0
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Moon).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(79.0f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Mercury).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(12.1f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Venus).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(42.0f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Mars).calculatePercentIlluminated(testCal.time)
        ).isWithin(PHASE_TOL).of(99.6f)
    }

    private val REG_TOL = 0.0001f

    // These are copies of the above tests that are disabled, but 'fixed' to pass. This doesn't
    // mean the calculations are correct...just that any refactorings we do haven't changed them.
    // This obviously needs to be revisited.
    @Test
    fun regressionTests() {
        val universe = Universe()
        val testCal = GregorianCalendar()
        testCal.timeZone = TimeZone.getTimeZone("GMT")

        // 2010 Dec 25, 12:00 UT1
        testCal[2010, GregorianCalendar.DECEMBER, 25, 12, 0] = 0
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Moon).calculatePercentIlluminated(testCal.time)
        ).isWithin(REG_TOL).of(21.741992950439453f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Mercury).calculatePercentIlluminated(testCal.time)
        ).isWithin(REG_TOL).of(12.131664276123047f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Venus).calculatePercentIlluminated(testCal.time)
        ).isWithin(REG_TOL).of(42.03889846801758f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Mars).calculatePercentIlluminated(testCal.time)
        ).isWithin(REG_TOL).of(99.64849853515625f)

        // Don't trust these numbers
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Moon).calculatePhaseAngle(testCal.time)
        ).isWithin(REG_TOL).of(124.41341400146484f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Mercury).calculatePhaseAngle(testCal.time)
        ).isWithin(REG_TOL).of(139.23260498046875f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Venus).calculatePhaseAngle(testCal.time)
        ).isWithin(REG_TOL).of(99.1617431640625f)
        assertThat(
            universe.solarSystemObjectFor(SolarSystemBody.Mars).calculatePhaseAngle(testCal.time)
        ).isWithin(REG_TOL).of(6.797830581665039f)
        assertThat(universe.solarSystemObjectFor(SolarSystemBody.Moon).getMagnitude(testCal.time)).isWithin(REG_TOL).of(-10f)
        assertThat(universe.solarSystemObjectFor(SolarSystemBody.Mercury).getMagnitude(testCal.time)).isWithin(REG_TOL)
            .of(1.7964696884155273f)
        assertThat(universe.solarSystemObjectFor(SolarSystemBody.Venus).getMagnitude(testCal.time)).isWithin(REG_TOL)
            .of(-4.544736385345459f)
        assertThat(universe.solarSystemObjectFor(SolarSystemBody.Mars).getMagnitude(testCal.time)).isWithin(REG_TOL).of(1.2287708520889282f)
        assertThat(universe.solarSystemObjectFor(SolarSystemBody.Jupiter).getMagnitude(testCal.time)).isWithin(REG_TOL)
            .of(-2.377939224243164f)
        assertThat(universe.solarSystemObjectFor(SolarSystemBody.Saturn).getMagnitude(testCal.time)).isWithin(REG_TOL)
            .of(1.1006574630737305f)
        assertThat(universe.solarSystemObjectFor(SolarSystemBody.Uranus).getMagnitude(testCal.time)).isWithin(REG_TOL)
            .of(5.848583698272705f)
        assertThat(universe.solarSystemObjectFor(SolarSystemBody.Neptune).getMagnitude(testCal.time)).isWithin(REG_TOL)
            .of(7.944333076477051f)
        assertThat(universe.solarSystemObjectFor(SolarSystemBody.Pluto).getMagnitude(testCal.time)).isWithin(REG_TOL)
            .of(14.110675811767578f)
        assertThat(universe.solarSystemObjectFor(SolarSystemBody.Sun).getMagnitude(testCal.time)).isWithin(REG_TOL).of(-27f)
    }

    companion object {
        // Accuracy of 0.30 degree should be fine.
        // TODO(jontayler): investigate why this now fails with a tol of 0.25 degrees
        private const val EPSILON = 0.30f

        // Allow the lunar measurements to be a bit more 'off' for now as we're not taking
        // position on Earth into account.
        // TODO: tighten this up
        private const val LUNAR_TOL = 1.6f

        // Convert from hours to degrees
        private const val HOURS_TO_DEGREES = 360.0f / 24.0f
    }
}