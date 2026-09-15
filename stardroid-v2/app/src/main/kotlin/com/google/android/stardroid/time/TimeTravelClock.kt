/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.time

/**
 * The simulated clock while time travel is engaged: a port of v1's `TimeTravelClock`. Time
 * plays through a thirteen-step speed ladder (a week/sec backward through frozen to a
 * week/sec forward); each query advances the simulated time by wall-clock elapsed × rate.
 *
 * Pure JVM: the wall clock arrives as [wallTimeMillis] so tests can drive it.
 */
class TimeTravelClock(
    private val wallTimeMillis: () -> Long,
) {
    private var speedIndex = STOPPED_INDEX
    private var timeLastSetMillis = 0L
    private var simulatedTimeMillis = 0L

    /** The current playback rate in simulated seconds per wall second; negative is backward. */
    val rateSecondsPerSecond: Double
        @Synchronized get() = SPEEDS[speedIndex]

    /** Jumps the simulated clock to [epochMillis] and freezes it there (v1 behavior). */
    @Synchronized
    fun setTimeTravelDate(epochMillis: Long) {
        pause()
        timeLastSetMillis = wallTimeMillis()
        simulatedTimeMillis = epochMillis
    }

    /**
     * Re-anchors the wall clock to now without disturbing the simulated time or speed. Called
     * when a transition arrives in time travel: the ladder may have advanced during the glide,
     * so anchoring here stops the accumulated wall gap from jumping the sky on the first tick.
     */
    @Synchronized
    fun resetAnchor() {
        timeLastSetMillis = wallTimeMillis()
    }

    /** One step up the ladder: faster forward, or less fast backward. */
    @Synchronized
    fun accelerate() {
        if (speedIndex < SPEEDS.lastIndex) speedIndex++
    }

    /** One step down the ladder: faster backward, or less fast forward. */
    @Synchronized
    fun decelerate() {
        if (speedIndex > 0) speedIndex--
    }

    /** Freezes simulated time. */
    @Synchronized
    fun pause() {
        speedIndex = STOPPED_INDEX
    }

    @Synchronized
    fun timeInMillisSinceEpoch(): Long {
        val now = wallTimeMillis()
        // Guard a backward wall-clock adjustment (NTP) from running simulated time backward.
        val elapsedMillis = (now - timeLastSetMillis).coerceAtLeast(0L)
        val rate = SPEEDS[speedIndex]
        var timeDeltaMillis = (rate * elapsedMillis).toLong()
        if (rate >= SECONDS_PER_DAY || rate <= -SECONDS_PER_DAY) {
            // At a day/sec and beyond, step in whole days so the map isn't dizzyingly fast —
            // this shows the slow annual procession of the stars (v1).
            val days = timeDeltaMillis / MILLISECONDS_PER_DAY
            if (days == 0L) {
                return simulatedTimeMillis
            }
            timeDeltaMillis = days * MILLISECONDS_PER_DAY
            // Advance the wall anchor only by the time the whole days actually consumed,
            // preserving the sub-day remainder — otherwise the clock runs slow at these rates.
            timeLastSetMillis += (timeDeltaMillis / rate).toLong()
        } else {
            timeLastSetMillis = now
        }
        simulatedTimeMillis += timeDeltaMillis
        return simulatedTimeMillis
    }

    companion object {
        const val SECONDS_PER_MINUTE = 60.0
        const val SECONDS_PER_10_MINUTES = 600.0
        const val SECONDS_PER_HOUR = 3600.0
        const val SECONDS_PER_DAY = 24.0 * SECONDS_PER_HOUR
        const val SECONDS_PER_WEEK = 7.0 * SECONDS_PER_DAY
        private const val MILLISECONDS_PER_DAY = 24L * 3600L * 1000L

        /** v1's ladder; the UI maps each rate to its label via the public rate constants above. */
        private val SPEEDS =
            doubleArrayOf(
                -SECONDS_PER_WEEK,
                -SECONDS_PER_DAY,
                -SECONDS_PER_HOUR,
                -SECONDS_PER_10_MINUTES,
                -SECONDS_PER_MINUTE,
                -1.0,
                0.0,
                1.0,
                SECONDS_PER_MINUTE,
                SECONDS_PER_10_MINUTES,
                SECONDS_PER_HOUR,
                SECONDS_PER_DAY,
                SECONDS_PER_WEEK,
            )

        private val STOPPED_INDEX = SPEEDS.size / 2
    }
}
