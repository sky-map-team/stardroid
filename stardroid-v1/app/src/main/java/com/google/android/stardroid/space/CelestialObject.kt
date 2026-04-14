package com.google.android.stardroid.space

import java.util.*

import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.math.*
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin


/**
 * Base class for any celestial objects.
 */
abstract class CelestialObject {
    abstract fun getRaDec(date : Date) : RaDec

    /**
     * Enum that identifies whether we are interested in rise or set time.
     */
    enum class RiseSetIndicator {
        RISE, SET
    }

    // Maximum number of times to calculate rise/set times. If we cannot
    // converge after this many iteretions, we will fail.
    private val MAX_ITERATIONS = 25

    /**
     * Calculates the next rise or set time of this planet from a given observer.
     * Returns null if the planet doesn't rise or set during the next day.
     *
     * @param now Calendar time from which to calculate next rise / set time.
     * @param loc Location of observer.
     * @param indicator Indicates whether to look for rise or set time.
     * @return New Calendar set to the next rise or set time if within
     * the next day, otherwise null.
     */
    open fun calcNextRiseSetTime(
        now: Calendar, loc: LatLong,
        indicator: RiseSetIndicator
    ): Calendar? {
        // Make a copy of the calendar to return.
        val riseSetTime = Calendar.getInstance()
        val riseSetUt = calcRiseSetTime(now.time, loc, indicator)
        // Early out if no nearby rise set time.
        if (riseSetUt < 0) {
            return null
        }

        // Find the start of this day in the local time zone. The (a / b) * b
        // formulation looks weird, it's using the properties of int arithmetic
        // so that (a / b) is really floor(a / b).
        val dayStart = now.timeInMillis / TimeConstants.MILLISECONDS_PER_DAY * TimeConstants.MILLISECONDS_PER_DAY - riseSetTime[Calendar.ZONE_OFFSET]
        val riseSetUtMillis = (calcRiseSetTime(now.time, loc, indicator)
                * TimeConstants.MILLISECONDS_PER_HOUR).toLong()
        var newTime = dayStart + riseSetUtMillis + riseSetTime[Calendar.ZONE_OFFSET]
        // If the newTime is before the current time, go forward 1 day.
        if (newTime < now.timeInMillis) {
            // Log.d(TAG, "Nearest Rise/Set is in the past. Adding one day.")
            newTime += TimeConstants.MILLISECONDS_PER_DAY
        }
        riseSetTime.timeInMillis = newTime
        if (!riseSetTime.after(now)) {
            //Log.e(
            //    TAG,
            //    "Next rise set time ($riseSetTime) should be after current time ($now)"
            //)
        }
        return riseSetTime
    }

    // Used in Rise/Set calculations
    open protected val bodySize = 0.0f

    // Internally calculate the rise and set time of an object.
    // Returns a double, the number of hours through the day in UT.
    private fun calcRiseSetTime(
        d: Date, loc: LatLong,
        indicator: RiseSetIndicator
    ): Double {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UT"))
        cal.time = d
        val sign = if (indicator == RiseSetIndicator.RISE) 1.0f else -1.0f
        var delta = 5.0f
        var ut = 12.0f
        var counter = 0
        while (Math.abs(delta) > 0.008 && counter < MAX_ITERATIONS) {
            cal[Calendar.HOUR_OF_DAY] = floor(ut).toInt()
            val minutes: Float = (ut - floor(ut)) * 60.0f
            cal[Calendar.MINUTE] = minutes.toInt()
            cal[Calendar.SECOND] = ((minutes - floor(minutes)) * 60f).toInt()

            // Calculate the hour angle and declination of the planet.
            // TODO(serafini): Need to fix this for arbitrary RA/Dec locations.
            val tmp = cal.time
            val (ra, dec) = getRaDec(tmp)

            // GHA = GST - RA. (In degrees.)
            val gst: Float = meanSiderealTime(tmp, 0f)
            val gha = gst - ra

            // The value of -0.83 works for the diameter of the Sun and Moon. We
            // assume that other objects are simply points.
            val bodySize = bodySize
            val hourAngle = calculateHourAngle(bodySize, loc.latitude, dec)
            delta = (gha + loc.longitude + sign * hourAngle) / 15.0f
            while (delta < -24.0f) {
                delta = delta + 24.0f
            }
            while (delta > 24.0f) {
                delta = delta - 24.0f
            }
            ut = ut - delta

            // I think we need to normalize UT
            while (ut < 0.0f) {
                ut = ut + 24.0f
            }
            while (ut > 24.0f) {
                ut = ut - 24.0f
            }
            ++counter
        }

        // Return failure if we didn't converge.
        if (counter == MAX_ITERATIONS) {
            //Log.d(TAG, "Rise/Set calculation didn't converge.")
            return (-1.0f).toDouble()
        }

        // TODO(serafini): Need to handle latitudes above 60
        // At latitudes above 60, we need to calculate the following:
        // sin h = sin phi sin delta + cos phi cos delta cos (gha + lambda)
        return ut.toDouble()
    }


    // Calculates the hour angle of a given declination for the given location.
    // This is a helper application for the rise and set calculations. Its
    // probably not worth using as a general purpose method.
    // All values are in degrees.
    //
    // This method calculates the hour angle from the meridian using the
    // following equation from the Astronomical Almanac (p487):
    // cos ha = (sin alt - sin lat * sin dec) / (cos lat * cos dec)
    open fun calculateHourAngle(
        altitude: Float, latitude: Float,
        declination: Float
    ): Float {
        val altRads: Float = altitude * DEGREES_TO_RADIANS
        val latRads: Float = latitude * DEGREES_TO_RADIANS
        val decRads: Float = declination * DEGREES_TO_RADIANS
        val cosHa: Float =
            (sin(altRads) - sin(latRads) * sin(decRads)) /
                    (cos(latRads) * cos(decRads))
        return RADIANS_TO_DEGREES * acos(cosHa)
    }
}