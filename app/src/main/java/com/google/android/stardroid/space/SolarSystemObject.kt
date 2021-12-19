package com.google.android.stardroid.space

import com.google.android.stardroid.base.VisibleForTesting
import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.*
import java.util.*

import com.google.android.stardroid.math.RaDec.Companion.fromGeocentricCoords
import kotlin.math.cos

import com.google.android.stardroid.math.Vector3
import kotlin.math.log10


/**
 * A celestial object that lives in our solar system.
 */
abstract class SolarSystemObject(protected val solarSystemBody : SolarSystemBody) : MovingObject() {
    fun getUpdateFrequencyMs(): Long {
        return solarSystemBody.updateFrequencyMs
    }

    /**
     * Returns the resource id for the string corresponding to the name of this
     * planet.
     */
    fun getNameResourceId(): Int {
        return solarSystemBody.nameResourceId
    }

    /** Returns the resource id for the planet's image.  */
    abstract fun getImageResourceId(time: Date): Int

    /**
     * Calculate the percent of the body that is illuminated. The value returned
     * is a fraction in the range from 0.0 to 100.0.
     */
    // TODO(serafini): Do we need this method?
    @VisibleForTesting
    open fun calculatePercentIlluminated(time: Date): Float {
        val phaseAngle: Float = this.calculatePhaseAngle(time)
        return 50.0f * (1.0f + cos(phaseAngle * DEGREES_TO_RADIANS))
    }

    /**
     * Calculates the phase angle of the planet, in degrees.
     */
    // TODO(jontayler): not clear why default viz doesn't work here.
    @VisibleForTesting
    open fun calculatePhaseAngle(time: Date): Float {
        // For the moon, we will approximate phase angle by calculating the
        // elongation of the moon relative to the sun. This is accurate to within
        // about 1%.
        // TODO(serafini): We need to correct the Ra/Dec for the user's location. The
        // current calculation is probably accurate to a degree or two, but we can,
        // and should, do better.
        if (solarSystemBody === SolarSystemBody.Moon) {
            val moonRaDec: RaDec = this.getRaDec(time)
            val moon: Vector3 = getGeocentricCoords(moonRaDec)
            val sunCoords: Vector3 =
                heliocentricCoordinatesFromOrbitalElements(SolarSystemBody.Earth.getOrbitalElements(time))
            val sunRaDec = fromGeocentricCoords(sunCoords)
            val (x, y, z) = getGeocentricCoords(sunRaDec)
            return 180.0f -
                    MathUtils.acos(x * moon.x + y * moon.y + z * moon.z) * RADIANS_TO_DEGREES
        }

        // First, determine position in the solar system.
        val planetCoords: Vector3 =
            heliocentricCoordinatesFromOrbitalElements(solarSystemBody.getOrbitalElements(time))

        // Second, determine position relative to Earth
        val earthCoords: Vector3 =
            heliocentricCoordinatesFromOrbitalElements(SolarSystemBody.Earth.getOrbitalElements(time))
        val earthDistance = planetCoords.distanceFrom(earthCoords)

        // Finally, calculate the phase of the body.
        // TODO(johntaylor): reexamine this.
        return MathUtils.acos(
            (earthDistance * earthDistance +
                    planetCoords.length2 -
                    earthCoords.length2) /
                    (2.0f * earthDistance * planetCoords.length)
        ) * RADIANS_TO_DEGREES
    }

    // TODO(serafini): This is experimental code used to scale planetary images.
    fun getPlanetaryImageSize(): Float {
        return when (this.solarSystemBody) {
            SolarSystemBody.Sun, SolarSystemBody.Moon -> 0.02f
            SolarSystemBody.Mercury, SolarSystemBody.Venus, SolarSystemBody.Mars, SolarSystemBody.Pluto -> 0.01f
            SolarSystemBody.Jupiter -> 0.025f
            SolarSystemBody.Uranus, SolarSystemBody.Neptune -> 0.015f
            SolarSystemBody.Saturn -> 0.035f
            else -> throw RuntimeException("Unknown image size for Solar System Object: $this")
        }
    }

    /**
     * Calculates the planet's magnitude for the given date.
     *
     * TODO(serafini): I need to re-factor this method so it uses the phase
     * calculations above. For now, I'm going to duplicate some code to avoid
     * some redundant calculations at run time.
     */
    open fun getMagnitude(time: Date): Float {
        // First, determine position in the solar system.
        val planetCoords = heliocentricCoordinatesFromOrbitalElements(solarSystemBody.getOrbitalElements(time))

        // Second, determine position relative to Earth
        val earthCoords =
            heliocentricCoordinatesFromOrbitalElements(SolarSystemBody.Earth.getOrbitalElements(time))
        val earthDistance = planetCoords.distanceFrom(earthCoords)

        // Third, calculate the phase of the body.
        val phase = MathUtils.acos(
            (earthDistance * earthDistance +
                    planetCoords.length2 -
                    earthCoords.length2) /
                    (2.0f * earthDistance * planetCoords.length)
        ) * RADIANS_TO_DEGREES
        val p = phase / 100.0f // Normalized phase angle

        // Finally, calculate the magnitude of the body.
        // Apparent visual magnitude
        var mag = when (this.solarSystemBody) {
            SolarSystemBody.Mercury -> -0.42f + (3.80f - (2.73f - 2.00f * p) * p) * p
            SolarSystemBody.Venus -> -4.40f + (0.09f + (2.39f - 0.65f * p) * p) * p
            SolarSystemBody.Mars -> -1.52f + 1.6f * p
            SolarSystemBody.Jupiter -> -9.40f + 0.5f * p
            SolarSystemBody.Saturn ->         // TODO(serafini): Add the real calculations that consider the position
                // of the rings. For now, lets assume the following, which gets us a reasonable
                // approximation of Saturn's magnitude for the near future.
                -8.75f
            SolarSystemBody.Uranus -> -7.19f
            SolarSystemBody.Neptune -> -6.87f
            SolarSystemBody.Pluto -> -1.0f
            else -> throw RuntimeException("Unknown magnitude for solar system body $this")
        }
        return mag + 5.0f * log10(planetCoords.length * earthDistance)
    }
}