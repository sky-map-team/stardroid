package com.google.android.stardroid.control

import com.google.android.stardroid.math.LatLong

/**
 * Represents the various states of the location-tracking system.
 *
 * ### State Transition Diagram
 * ```
 * [Unset] --------------------(startAuto)-------------> [Acquiring]
 * [Unset] ----------------(setManualLocation)---------> [Confirmed(MANUAL)]
 * [Unset] --------------------(startAuto)-------------> [HardwareUnavailable]
 *
 * [Acquiring] ----------------(location found)--------> [Confirmed(AUTO)]
 * [Acquiring] ----------------(timeout)---------------> [AcquiringTimeout]
 * [Acquiring] ----------------(permission denied)------> [PermissionDenied]
 * [Acquiring] ----------------(permission perm denied)-> [PermissionPermanentlyDenied]
 *
 * [AcquiringTimeout] ---------(keepWaiting)-----------> [Acquiring]
 *
 * [Confirmed(AUTO/MANUAL)] ---(setManualLocation)------> [Confirmed(MANUAL)]
 * [Confirmed(MANUAL)] --------(switchToAuto)-----------> [Acquiring]
 * ```
 */
sealed class LocationState {

    /**
     * Initial state. No location has been set and no attempt to acquire one is in progress.
     */
    data object Unset : LocationState()

    /**
     * Actively attempting to acquire a location from the device's location providers.
     */
    data object Acquiring : LocationState()

    /**
     * A location has been successfully determined.
     * @property location The latitude and longitude.
     * @property source Whether the location was determined automatically or set manually.
     * @property accuracy The accuracy in meters, if available.
     * @property timestamp The time the location was acquired.
     */
    data class Confirmed(
        val location: LatLong,
        val source: LocationSource,
        val accuracy: Float?,
        val timestamp: Long
    ) : LocationState()

    /**
     * The user denied the required location permissions, but we can ask again.
     */
    data object PermissionDenied : LocationState()

    /**
     * The user denied the required location permissions and selected "Don't ask again".
     */
    data object PermissionPermanentlyDenied : LocationState()

    /**
     * The device does not have the necessary hardware (e.g., GPS) or it is disabled.
     */
    data object HardwareUnavailable : LocationState()

    /**
     * An attempt to acquire a location took longer than the defined timeout.
     */
    data object AcquiringTimeout : LocationState()
}

/**
 * The source of a [LocationState.Confirmed] location.
 */
enum class LocationSource {
    /** Location was automatically determined via GPS or Network. */
    AUTO,
    /** Location was manually entered by the user. */
    MANUAL
}
