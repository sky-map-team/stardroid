/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** How much the phone's resolved pointing is wobbling, in degrees, over the trailing window. */
data class PointingJitter(val azimuthStdDevDeg: Double, val altitudeStdDevDeg: Double)

/**
 * A rolling standard deviation of azimuth/altitude over [windowMillis] — the number that
 * answers "how much is the view actually wobbling," independent of which way the phone happens
 * to be pointed.
 *
 * Azimuth is a circular quantity (0° and 360° are the same heading), so a plain standard
 * deviation would blow up for a phone jittering around due north. [azimuthStdDevDeg] instead
 * uses the standard circular-statistics construction: average the samples as unit vectors, take
 * the length of that average (1.0 for no spread, 0.0 for uniformly scattered), and convert back
 * to an angular spread. Altitude never wraps, so [altitudeStdDevDeg] is an ordinary population
 * standard deviation.
 */
class PointingJitterAccumulator(private val windowMillis: Long) {
    private data class Sample(val timeMillis: Long, val azimuthDeg: Double, val altitudeDeg: Double)

    private val samples = ArrayDeque<Sample>()

    fun add(
        timeMillis: Long,
        azimuthDeg: Double,
        altitudeDeg: Double,
    ): PointingJitter {
        samples.addLast(Sample(timeMillis, azimuthDeg, altitudeDeg))
        while (samples.size > 1 && timeMillis - samples.first().timeMillis > windowMillis) {
            samples.removeFirst()
        }
        // Iterates samples directly rather than via two `.map { }` passes — this runs on every
        // sensor event, and a jittery phone can mean hundreds of these a second.
        return PointingJitter(
            azimuthStdDevDeg = circularStdDevDeg(),
            altitudeStdDevDeg = linearStdDevDeg(),
        )
    }

    private fun circularStdDevDeg(): Double {
        var sumCos = 0.0
        var sumSin = 0.0
        for (sample in samples) {
            val angleRad = sample.azimuthDeg * DEGREES_TO_RADIANS
            sumCos += cos(angleRad)
            sumSin += sin(angleRad)
        }
        val n = samples.size
        // The resultant length R is 1.0 for no spread at all and 0.0 once the samples are
        // uniformly scattered around the circle; sqrt(-2 ln R) is the standard mapping from
        // R back to an angular standard deviation (Mardia & Jupp, Directional Statistics).
        val resultantLength = (sqrt(sumCos * sumCos + sumSin * sumSin) / n).coerceIn(1e-9, 1.0)
        return sqrt(-2.0 * ln(resultantLength)) * RADIANS_TO_DEGREES
    }

    private fun linearStdDevDeg(): Double {
        val mean = samples.sumOf { it.altitudeDeg } / samples.size
        val variance =
            samples.sumOf { (it.altitudeDeg - mean) * (it.altitudeDeg - mean) } / samples.size
        return sqrt(variance)
    }
}
