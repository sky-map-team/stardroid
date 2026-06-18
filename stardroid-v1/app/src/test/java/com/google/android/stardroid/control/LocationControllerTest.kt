package com.google.android.stardroid.control

import android.app.Activity
import android.content.SharedPreferences
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.math.LatLong
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.ScheduledExecutorService

@RunWith(RobolectricTestRunner::class)
class LocationControllerTest {

    @Mock private lateinit var locationProvider: LocationProvider
    @Mock private lateinit var astronomerModel: AstronomerModel
    @Mock private lateinit var activity: Activity
    @Mock private lateinit var preferences: SharedPreferences
    @Mock private lateinit var prefEditor: SharedPreferences.Editor
    @Mock private lateinit var backgroundExecutor: ScheduledExecutorService

    private lateinit var controller: LocationController

    private val london = LatLong(51.5f, -0.1f)
    private val tokyo = LatLong(35.7f, 139.7f)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(preferences.getBoolean(anyString(), anyBoolean())).thenReturn(false)
        `when`(preferences.getString(anyString(), anyString())).thenReturn("")
        `when`(preferences.edit()).thenReturn(prefEditor)
        `when`(prefEditor.putString(anyString(), anyString())).thenReturn(prefEditor)
        `when`(prefEditor.putBoolean(anyString(), anyBoolean())).thenReturn(prefEditor)
        `when`(activity.applicationContext).thenReturn(RuntimeEnvironment.getApplication())
        `when`(locationProvider.isAvailable()).thenReturn(true)

        controller = LocationController(
            locationProvider, astronomerModel, preferences, backgroundExecutor, activity)
        controller.setModel(astronomerModel)
    }

    // --- Hardware unavailable ---

    @Test
    fun start_whenProviderUnavailable_transitionsToHardwareUnavailable() {
        `when`(locationProvider.isAvailable()).thenReturn(false)
        controller.start()
        assertThat(controller.currentState()).isInstanceOf(LocationState.HardwareUnavailable::class.java)
    }

    @Test
    fun start_whenProviderUnavailable_doesNotRequestUpdates() {
        `when`(locationProvider.isAvailable()).thenReturn(false)
        controller.start()
        // isAvailable() was called; no further interactions should happen
        verify(locationProvider).isAvailable()
        verifyNoInteractions(astronomerModel)
    }

    // --- Acquiring state ---

    @Test
    fun start_withPermissionAndProvider_transitionsToAcquiring() {
        controller.start()
        assertThat(controller.currentState()).isInstanceOf(LocationState.Acquiring::class.java)
    }

    // --- Permission denied ---

    @Test
    fun onPermissionDenied_canAskTrue_transitionsToPermissionDenied() {
        controller.onPermissionDenied(canAsk = true)
        assertThat(controller.currentState()).isInstanceOf(LocationState.PermissionDenied::class.java)
    }

    @Test
    fun onPermissionDenied_canAskFalse_transitionsToPermissionPermanentlyDenied() {
        controller.onPermissionDenied(canAsk = false)
        assertThat(controller.currentState())
            .isInstanceOf(LocationState.PermissionPermanentlyDenied::class.java)
    }

    @Test
    fun onPermissionRevoked_transitionsToPermissionDenied() {
        controller.onPermissionRevoked()
        assertThat(controller.currentState()).isInstanceOf(LocationState.PermissionDenied::class.java)
    }

    // --- Updates ---

    @Test
    fun locationUpdate_firstFix_alwaysUpdatesModel() {
        controller.start()
        controller.testOnlyInvokeLocationUpdate(london, null)
        verify(astronomerModel).setLocation(london)
    }

    @Test
    fun locationUpdate_alwaysUpdatesState() {
        controller.start()
        controller.testOnlyInvokeLocationUpdate(london, null)
        // Even a tiny move updates state now, as we rely on the provider's own filtering.
        val veryClose = LatLong(51.50001f, -0.1f)
        controller.testOnlyInvokeLocationUpdate(veryClose, null)
        val state = controller.currentState() as LocationState.Confirmed
        assertThat(state.location).isEqualTo(veryClose)
    }

    // --- Manual location ---

    @Test
    fun setManualLocation_transitionsToConfirmedManual() {
        controller.setManualLocation(london)
        val state = controller.currentState() as LocationState.Confirmed
        assertThat(state.source).isEqualTo(LocationSource.MANUAL)
        assertThat(state.accuracy).isNull()
        assertThat(state.location).isEqualTo(london)
    }

    @Test
    fun setManualLocation_updatesAstronomerModel() {
        controller.setManualLocation(london)
        verify(astronomerModel).setLocation(london)
    }

    @Test
    fun setManualLocation_savesPreferences() {
        controller.setManualLocation(london)
        verify(prefEditor).putString(ApplicationConstants.LATITUDE_PREF_KEY, london.latitude.toString())
        verify(prefEditor).putString(ApplicationConstants.LONGITUDE_PREF_KEY, london.longitude.toString())
        verify(prefEditor).putBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF_KEY, true)
    }
}
