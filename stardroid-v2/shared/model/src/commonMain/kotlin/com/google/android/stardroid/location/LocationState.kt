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

/**
 * The location system's user-visible states — the v1 `LocationState` hierarchy ported whole,
 * including its transition diagram:
 *
 * ```
 * [Unset] ------------------(startAuto)---------------> [Acquiring]
 * [Unset] --------------(setManualLocation)-----------> [Confirmed(MANUAL)]
 * [Unset] ------------------(startAuto)---------------> [HardwareUnavailable]
 *
 * [Acquiring] --------------(location found)----------> [Confirmed(AUTO)]
 * [Acquiring] --------------(timeout)------------------> [AcquiringTimeout]
 * [Acquiring] --------------(permission denied)--------> [PermissionDenied]
 * [Acquiring] --------------(permission perm denied)---> [PermissionPermanentlyDenied]
 *
 * [AcquiringTimeout] -------(keepWaiting)--------------> [Acquiring]
 *
 * [Confirmed(AUTO/MANUAL)] -(setManualLocation)--------> [Confirmed(MANUAL)]
 * [Confirmed(MANUAL)] ------(switchToAuto)-------------> [Acquiring]
 * ```
 */
sealed class LocationState {
    /** No location has been set and no attempt to acquire one is in progress. */
    data object Unset : LocationState()

    /** Actively waiting for a fix from the device's location providers. */
    data object Acquiring : LocationState()

    /**
     * A location is set.
     *
     * @property location the observer's position.
     * @property source automatic fix or manual entry.
     * @property accuracyM the fix's horizontal accuracy in metres, null if unknown or manual.
     * @property timestampMillis wall time the location was set.
     */
    data class Confirmed(
        val location: LatLong,
        val source: LocationSource,
        val accuracyM: Float?,
        val timestampMillis: Long,
    ) : LocationState()

    /** The user denied the location permission, but the system will let us ask again. */
    data object PermissionDenied : LocationState()

    /** The user denied the location permission and the system won't re-prompt. */
    data object PermissionPermanentlyDenied : LocationState()

    /** No enabled location provider on this device. */
    data object HardwareUnavailable : LocationState()

    /** No fix arrived within the acquiring timeout; the user chooses to wait or type. */
    data object AcquiringTimeout : LocationState()
}

/** How a [LocationState.Confirmed] location was determined. */
enum class LocationSource {
    /** From the device's location providers. */
    AUTO,

    /** Typed (or geocoded) by the user. */
    MANUAL,
}
