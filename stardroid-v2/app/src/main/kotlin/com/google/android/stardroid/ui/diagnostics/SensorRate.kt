/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

/**
 * How often a sensor is actually delivering events, and how long it's been since the last one —
 * the number that catches a sensor gone silent, which a raw-value row alone doesn't: a stuck
 * accelerometer still shows a plausible-looking last reading forever.
 */
data class SensorRateInfo(val hz: Double, val staleForMillis: Long?)

/**
 * Tracks event arrivals for one sensor over a trailing [windowMillis], for [SensorRateInfo].
 *
 * No locking: like [PointingJitterAccumulator], this is driven from a single ViewModel-scoped
 * flow collection on the main dispatcher — [recordEvent] (from the sensor's `onEach`) and
 * [snapshot] (from the periodic diagnostics tick) never run concurrently in practice.
 */
class SensorRateAccumulator(private val windowMillis: Long) {
    private var lastEventMillis: Long? = null
    private val eventTimes = ArrayDeque<Long>()

    fun recordEvent(nowMillis: Long) {
        lastEventMillis = nowMillis
        eventTimes.addLast(nowMillis)
        while (eventTimes.size > 1 && nowMillis - eventTimes.first() > windowMillis) {
            eventTimes.removeFirst()
        }
    }

    fun snapshot(nowMillis: Long): SensorRateInfo {
        // recordEvent only prunes against the *arriving* event's timestamp, so a sensor that
        // has gone silent would otherwise leave a stale window sitting here forever, computed
        // fresh into a plausible-looking Hz on every poll — exactly the frozen-reading problem
        // this feature exists to catch, just for the rate instead of the raw value.
        while (eventTimes.size > 1 && nowMillis - eventTimes.first() > windowMillis) {
            eventTimes.removeFirst()
        }
        val hz =
            if (eventTimes.size < 2) {
                0.0
            } else {
                val elapsedSeconds = (eventTimes.last() - eventTimes.first()) / 1000.0
                if (elapsedSeconds <= 0.0) 0.0 else (eventTimes.size - 1) / elapsedSeconds
            }
        return SensorRateInfo(hz, lastEventMillis?.let { nowMillis - it })
    }
}
