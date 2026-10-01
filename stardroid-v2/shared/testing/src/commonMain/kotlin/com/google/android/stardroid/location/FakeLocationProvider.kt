/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.location

import com.google.android.stardroid.math.LatLong

/** Hand-driven [LocationProvider]: tests push fixes through [sendFix]. */
class FakeLocationProvider : LocationProvider {
    var available = true
    var updating = false
        private set
    var startCount = 0
        private set
    private var callback: ((LatLong, Float?) -> Unit)? = null

    override fun startUpdates(
        minDistanceMetres: Float,
        onUpdate: (LatLong, Float?) -> Unit,
    ) {
        updating = true
        startCount++
        callback = onUpdate
    }

    override fun stopUpdates() {
        updating = false
        callback = null
    }

    override fun isAvailable(): Boolean = available

    fun sendFix(
        location: LatLong,
        accuracyM: Float? = null,
    ) {
        callback?.invoke(location, accuracyM)
    }
}
