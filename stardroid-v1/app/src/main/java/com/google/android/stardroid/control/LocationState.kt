package com.google.android.stardroid.control

import com.google.android.stardroid.math.LatLong

sealed class LocationState {

    data object Unset : LocationState()

    object Acquiring : LocationState()

    data class Confirmed(
        val location: LatLong,
        val source: LocationSource,
        val accuracy: Float?,
        val timestamp: Long
    ) : LocationState()

    object PermissionDenied : LocationState()

    object PermissionPermanentlyDenied : LocationState()

    object HardwareUnavailable : LocationState()

    object AcquiringTimeout : LocationState()
}

enum class LocationSource {
    AUTO,
    MANUAL
}
