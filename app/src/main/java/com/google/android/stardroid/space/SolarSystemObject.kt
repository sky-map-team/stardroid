package com.google.android.stardroid.space

import android.util.Log
import com.google.android.stardroid.base.VisibleForTesting
import com.google.android.stardroid.ephemeris.Planet
import com.google.android.stardroid.math.*
import java.util.*

import com.google.android.stardroid.math.RaDec.Companion.calculateRaDecDist
import kotlin.math.cos
import com.google.android.stardroid.space.Moon
import com.google.android.stardroid.util.MiscUtil

import com.google.android.stardroid.math.Vector3








/**
 * A celestial object that lives in our solar system.
 */
abstract class SolarSystemObject(protected val planet : Planet) : MovingObject() {
    fun getUpdateFrequencyMs(): Long {
        return planet.updateFrequencyMs
    }

    /**
     * Returns the resource id for the string corresponding to the name of this
     * planet.
     */
    fun getNameResourceId(): Int {
        return planet.nameResourceId
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
        if (planet === Planet.Moon) {
            val moonRaDec: RaDec = this.getRaDec(time)
            val moon: Vector3 = getGeocentricCoords(moonRaDec)
            val sunCoords: Vector3 =
                heliocentricCoordinatesFromOrbitalElements(Planet.Sun.getOrbitalElements(time))
            val sunRaDec = calculateRaDecDist(sunCoords)
            val (x, y, z) = getGeocentricCoords(sunRaDec)
            return 180.0f -
                    MathUtils.acos(x * moon.x + y * moon.y + z * moon.z) * RADIANS_TO_DEGREES
        }

        // First, determine position in the solar system.
        val planetCoords: Vector3 =
            heliocentricCoordinatesFromOrbitalElements(planet.getOrbitalElements(time))

        // Second, determine position relative to Earth
        val earthCoords: Vector3 =
            heliocentricCoordinatesFromOrbitalElements(Planet.Sun.getOrbitalElements(time))
        val earthDistance = planetCoords.distanceFrom(earthCoords)

        // Finally, calculate the phase of the body.
        return MathUtils.acos(
            (earthDistance * earthDistance +
                    planetCoords.length2 -
                    earthCoords.length2) /
                    (2.0f * earthDistance * planetCoords.length)
        ) * RADIANS_TO_DEGREES
    }

    // TODO(serafini): This is experimental code used to scale planetary images.
    fun getPlanetaryImageSize(): Float {
        return when (this.planet) {
            Planet.Sun, Planet.Moon -> 0.02f
            Planet.Mercury, Planet.Venus, Planet.Mars, Planet.Pluto -> 0.01f
            Planet.Jupiter -> 0.025f
            Planet.Uranus, Planet.Neptune -> 0.015f
            Planet.Saturn -> 0.035f
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
        val planetCoords = heliocentricCoordinatesFromOrbitalElements(planet.getOrbitalElements(time))

        // Second, determine position relative to Earth
        val earthCoords =
            heliocentricCoordinatesFromOrbitalElements(Planet.Sun.getOrbitalElements(time))
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
        var mag = when (this.planet) {
            Planet.Mercury -> -0.42f + (3.80f - (2.73f - 2.00f * p) * p) * p
            Planet.Venus -> -4.40f + (0.09f + (2.39f - 0.65f * p) * p) * p
            Planet.Mars -> -1.52f + 1.6f * p
            Planet.Jupiter -> -9.40f + 0.5f * p
            Planet.Saturn ->         // TODO(serafini): Add the real calculations that consider the position
                // of the rings. For now, lets assume the following, which gets us a reasonable
                // approximation of Saturn's magnitude for the near future.
                -8.75f
            Planet.Uranus -> -7.19f
            Planet.Neptune -> -6.87f
            Planet.Pluto -> -1.0f
            else -> {
                Log.e(MiscUtil.getTag(this), "Invalid planet: $this")
                // At least make it faint!
                100f
            }
        }
        return mag + 5.0f * MathUtils.log10(planetCoords.length * earthDistance)
    }
}