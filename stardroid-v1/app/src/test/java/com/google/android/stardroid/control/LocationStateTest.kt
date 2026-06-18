package com.google.android.stardroid.control

import com.google.android.stardroid.math.LatLong
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocationStateTest {

    private val sampleLocation = LatLong(51.5f, -0.1f)
    private val anotherLocation = LatLong(40.7f, -74.0f)

    @Test
    fun unset_isSingleton() {
        assertThat(LocationState.Unset).isSameInstanceAs(LocationState.Unset)
    }

    @Test
    fun acquiring_isSingleton() {
        assertThat(LocationState.Acquiring).isSameInstanceAs(LocationState.Acquiring)
    }

    @Test
    fun permissionDenied_isSingleton() {
        assertThat(LocationState.PermissionDenied).isSameInstanceAs(LocationState.PermissionDenied)
    }

    @Test
    fun permissionPermanentlyDenied_isSingleton() {
        assertThat(LocationState.PermissionPermanentlyDenied)
            .isSameInstanceAs(LocationState.PermissionPermanentlyDenied)
    }

    @Test
    fun hardwareUnavailable_isSingleton() {
        assertThat(LocationState.HardwareUnavailable)
            .isSameInstanceAs(LocationState.HardwareUnavailable)
    }

    @Test
    fun acquiringTimeout_isSingleton() {
        assertThat(LocationState.AcquiringTimeout).isSameInstanceAs(LocationState.AcquiringTimeout)
    }

    @Test
    fun confirmed_equalityBasedOnFields() {
        val ts = System.currentTimeMillis()
        val a = LocationState.Confirmed(sampleLocation, LocationSource.AUTO, 50f, ts)
        val b = LocationState.Confirmed(sampleLocation, LocationSource.AUTO, 50f, ts)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun confirmed_differentLocations_notEqual() {
        val ts = System.currentTimeMillis()
        val a = LocationState.Confirmed(sampleLocation, LocationSource.AUTO, 50f, ts)
        val b = LocationState.Confirmed(anotherLocation, LocationSource.AUTO, 50f, ts)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun confirmed_manualHasNullAccuracy() {
        val confirmed = LocationState.Confirmed(sampleLocation, LocationSource.MANUAL, null, 0L)
        assertThat(confirmed.accuracy).isNull()
        assertThat(confirmed.source).isEqualTo(LocationSource.MANUAL)
    }

    @Test
    fun confirmed_autoHasAccuracy() {
        val confirmed = LocationState.Confirmed(sampleLocation, LocationSource.AUTO, 25f, 0L)
        assertThat(confirmed.accuracy).isEqualTo(25f)
        assertThat(confirmed.source).isEqualTo(LocationSource.AUTO)
    }

    @Test
    fun locationSource_values() {
        assertThat(LocationSource.values()).asList()
            .containsExactly(LocationSource.AUTO, LocationSource.MANUAL)
    }

    @Test
    fun sealedClass_coversAllStates() {
        // Ensure all expected states exist by exercising each branch
        val states: List<LocationState> = listOf(
            LocationState.Unset,
            LocationState.Acquiring,
            LocationState.Confirmed(sampleLocation, LocationSource.AUTO, null, 0L),
            LocationState.PermissionDenied,
            LocationState.PermissionPermanentlyDenied,
            LocationState.HardwareUnavailable,
            LocationState.AcquiringTimeout
        )
        assertThat(states).hasSize(7)
    }

    @Test
    fun whenExpression_exhaustive() {
        // If a new state is added without updating this function the compiler will catch it
        fun describe(s: LocationState): String = when (s) {
            is LocationState.Unset -> "unset"
            is LocationState.Acquiring -> "acquiring"
            is LocationState.Confirmed -> "confirmed"
            is LocationState.PermissionDenied -> "denied"
            is LocationState.PermissionPermanentlyDenied -> "permanent"
            is LocationState.HardwareUnavailable -> "hardware"
            is LocationState.AcquiringTimeout -> "timeout"
        }
        assertThat(describe(LocationState.Unset)).isEqualTo("unset")
        assertThat(describe(LocationState.AcquiringTimeout)).isEqualTo("timeout")
    }
}
