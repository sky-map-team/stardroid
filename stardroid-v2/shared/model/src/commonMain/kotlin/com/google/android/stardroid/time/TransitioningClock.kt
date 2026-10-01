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
 * A clock that glides between real time and [TimeTravelClock] time: a port of v1's
 * `TransitioningCompositeClock`. Entering or leaving time travel interpolates over
 * [TRANSITION_TIME_MILLIS] of wall time with a smoothstep (zero-velocity endpoints), so the
 * sky sweeps to its destination instead of snapping.
 */
class TransitioningClock(
    private val timeTravelClock: TimeTravelClock,
    private val wallTimeMillis: () -> Long,
) {
    enum class Mode { REAL_TIME, TRANSITION, TIME_TRAVEL }

    private val lock = ReentrantLock()
    private var currentMode = Mode.REAL_TIME
    private var transitionTo = Mode.REAL_TIME
    private var startTimeMillis = 0L
    private var endTimeMillis = 0L
    private var startTransitionWallTimeMillis = 0L

    /** The mode with any completed transition resolved. */
    val mode: Mode
        get() =
            lock.withLock {
                resolveTransition()
                currentMode
            }

    /** Begins the glide from the current time to [targetMillis], where travel time holds. */
    fun goTimeTravel(targetMillis: Long) =
        lock.withLock {
            startTimeMillis = timeInMillisSinceEpoch()
            endTimeMillis = targetMillis
            timeTravelClock.setTimeTravelDate(targetMillis)
            transitionTo = Mode.TIME_TRAVEL
            currentMode = Mode.TRANSITION
            startTransitionWallTimeMillis = wallTimeMillis()
        }

    /** Begins the glide from the current (travel) time back to the wall clock. */
    fun returnToRealTime() =
        lock.withLock {
            startTimeMillis = timeInMillisSinceEpoch()
            endTimeMillis = wallTimeMillis() + TRANSITION_TIME_MILLIS
            transitionTo = Mode.REAL_TIME
            currentMode = Mode.TRANSITION
            startTransitionWallTimeMillis = wallTimeMillis()
        }

    fun timeInMillisSinceEpoch(): Long =
        lock.withLock {
            resolveTransition()
            if (currentMode == Mode.TRANSITION) {
                // Clamp to [0, TRANSITION_TIME_MILLIS]: a backward wall-clock (NTP) adjustment or a
                // few ms of drift past the resolveTransition() check would otherwise push lambda
                // outside [0, 1], where the smoothstep overshoots and pulls the time backward.
                val elapsedMillis =
                    (wallTimeMillis() - startTransitionWallTimeMillis)
                        .coerceIn(0L, TRANSITION_TIME_MILLIS)
                return@withLock interpolate(
                    startTimeMillis.toDouble(),
                    endTimeMillis.toDouble(),
                    elapsedMillis.toDouble() / TRANSITION_TIME_MILLIS,
                ).toLong()
            }
            return@withLock when (currentMode) {
                Mode.TIME_TRAVEL -> timeTravelClock.timeInMillisSinceEpoch()
                else -> wallTimeMillis()
            }
        }

    private fun resolveTransition() {
        if (currentMode == Mode.TRANSITION &&
            wallTimeMillis() - startTransitionWallTimeMillis > TRANSITION_TIME_MILLIS
        ) {
            currentMode = transitionTo
            if (currentMode == Mode.TIME_TRAVEL) timeTravelClock.resetAnchor()
        }
    }

    companion object {
        const val TRANSITION_TIME_MILLIS = 2500L

        /** Smoothstep from [start] at `lambda = 0` to [end] at `lambda = 1` (v1's curve). */
        fun interpolate(
            start: Double,
            end: Double,
            lambda: Double,
        ): Double = start + (lambda * lambda * (3.0 - 2.0 * lambda)) * (end - start)
    }
}
