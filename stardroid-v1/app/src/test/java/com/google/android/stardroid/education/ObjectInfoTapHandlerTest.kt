// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.education

import android.content.SharedPreferences
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.util.AnalyticsInterface
import com.google.android.stardroid.util.Experiment
import com.google.android.stardroid.util.ExperimentConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ObjectInfoTapHandler].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ObjectInfoTapHandlerTest {

    @Mock
    private lateinit var mockPreferences: SharedPreferences

    @Mock
    private lateinit var mockHitTester: CelestialHitTester

    @Mock
    private lateinit var mockListener: ObjectInfoTapHandler.ObjectTapListener

    @Mock
    private lateinit var mockAnalytics: AnalyticsInterface

    @Mock
    private lateinit var mockExperimentConfig: ExperimentConfig

    private lateinit var tapHandler: ObjectInfoTapHandler

    private val testObjectInfo = ObjectInfo(
        id = "mars",
        name = "Mars",
        description = "The Red Planet",
        funFact = "Has Olympus Mons",
        type = ObjectType.PLANET,
        distance = "228M km",
        size = "6,779 km",
        mass = "6.39 × 10²³ kg"
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // Default: disable the central-region restriction (whole screen active) so the existing
        // tests below exercise the hit-testing flow regardless of tap coordinates. Region-specific
        // behavior is covered by the dedicated tests further down. Stub with concrete args (not
        // matchers) to avoid Kotlin's non-null parameter check tripping on a null matcher value.
        `when`(mockExperimentConfig.getDouble(
            Experiment.INFO_CARD_TAP_REGION_FRACTION,
            ObjectInfoTapHandler.DEFAULT_TAP_REGION_FRACTION))
            .thenReturn(1.0)
        tapHandler = ObjectInfoTapHandler(
            mockPreferences, mockHitTester, mockAnalytics, mockExperimentConfig)
        tapHandler.setObjectTapListener(mockListener)
    }

    @Test
    fun testHandleTap_featureDisabled_returnsFalse() {
        `when`(mockPreferences.getBoolean(
            eq(ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY),
            anyBoolean()))
            .thenReturn(false)

        val result = tapHandler.handleTap(100f, 200f, 1080, 1920)

        assertThat(result).isFalse()
        verify(mockHitTester, never()).findObjectAtScreenPosition(
            100f, 200f, 1080, 1920)
    }

    @Test
    fun testHandleTap_autoMode_autoModeInfoDisabled_returnsFalse() {
        `when`(mockPreferences.getBoolean(
            eq(ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY),
            anyBoolean()))            .thenReturn(true)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.AUTO_MODE_PREF_KEY, true))
            .thenReturn(true)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.SHOW_OBJECT_INFO_AUTO_MODE_PREF_KEY, false))
            .thenReturn(false)

        val result = tapHandler.handleTap(100f, 200f, 1080, 1920)

        assertThat(result).isFalse()
        verify(mockHitTester, never()).findObjectAtScreenPosition(
            100f, 200f, 1080, 1920)
    }

    @Test
    fun testHandleTap_autoMode_autoModeInfoEnabled_objectFound_returnsTrue() {
        `when`(mockPreferences.getBoolean(
            eq(ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY),
            anyBoolean()))            .thenReturn(true)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.AUTO_MODE_PREF_KEY, true))
            .thenReturn(true)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.SHOW_OBJECT_INFO_AUTO_MODE_PREF_KEY, false))
            .thenReturn(true)
        `when`(mockHitTester.findObjectAtScreenPosition(100f, 200f, 1080, 1920))
            .thenReturn(testObjectInfo)

        val result = tapHandler.handleTap(100f, 200f, 1080, 1920)

        assertThat(result).isTrue()
        verify(mockListener).onObjectTapped(testObjectInfo)
    }

    @Test
    fun testHandleTap_autoMode_autoModeInfoEnabled_noObjectFound_returnsFalse() {
        `when`(mockPreferences.getBoolean(
            eq(ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY),
            anyBoolean()))            .thenReturn(true)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.AUTO_MODE_PREF_KEY, true))
            .thenReturn(true)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.SHOW_OBJECT_INFO_AUTO_MODE_PREF_KEY, false))
            .thenReturn(true)
        `when`(mockHitTester.findObjectAtScreenPosition(100f, 200f, 1080, 1920))
            .thenReturn(null)

        val result = tapHandler.handleTap(100f, 200f, 1080, 1920)

        assertThat(result).isFalse()
        verify(mockListener, never()).onObjectTapped(testObjectInfo)
    }

    @Test
    fun testHandleTap_manualMode_objectFound_returnsTrue() {
        `when`(mockPreferences.getBoolean(
            eq(ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY),
            anyBoolean()))            .thenReturn(true)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.AUTO_MODE_PREF_KEY, true))
            .thenReturn(false)
        `when`(mockHitTester.findObjectAtScreenPosition(100f, 200f, 1080, 1920))
            .thenReturn(testObjectInfo)

        val result = tapHandler.handleTap(100f, 200f, 1080, 1920)

        assertThat(result).isTrue()
        verify(mockListener).onObjectTapped(testObjectInfo)
    }

    @Test
    fun testHandleTap_manualMode_noObjectFound_returnsFalse() {
        `when`(mockPreferences.getBoolean(
            eq(ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY),
            anyBoolean()))            .thenReturn(true)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.AUTO_MODE_PREF_KEY, true))
            .thenReturn(false)
        `when`(mockHitTester.findObjectAtScreenPosition(100f, 200f, 1080, 1920))
            .thenReturn(null)

        val result = tapHandler.handleTap(100f, 200f, 1080, 1920)

        assertThat(result).isFalse()
        verify(mockListener, never()).onObjectTapped(testObjectInfo)
    }

    @Test
    fun testIsFeatureEnabled_enabled() {
        `when`(mockPreferences.getBoolean(
            eq(ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY),
            anyBoolean()))
            .thenReturn(true)

        assertThat(tapHandler.isFeatureEnabled()).isTrue()
    }

    @Test
    fun testIsFeatureEnabled_disabled() {
        `when`(mockPreferences.getBoolean(
            eq(ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY),
            anyBoolean()))
            .thenReturn(false)

        assertThat(tapHandler.isFeatureEnabled()).isFalse()
    }

    @Test
    fun testHandleTap_noListener_doesNotCrash() {
        tapHandler.setObjectTapListener(null)
        `when`(mockPreferences.getBoolean(
            eq(ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY),
            anyBoolean()))
            .thenReturn(true)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.AUTO_MODE_PREF_KEY, true))
            .thenReturn(false)
        `when`(mockHitTester.findObjectAtScreenPosition(100f, 200f, 1080, 1920))
            .thenReturn(testObjectInfo)

        // Should not throw
        val result = tapHandler.handleTap(100f, 200f, 1080, 1920)
        assertThat(result).isTrue()
    }

    /** Enables the feature in manual mode so taps reach the central-region check. */
    private fun enableFeatureInManualMode() {
        `when`(mockPreferences.getBoolean(
            eq(ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY), anyBoolean()))
            .thenReturn(true)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.AUTO_MODE_PREF_KEY, true))
            .thenReturn(false)
    }

    @Test
    fun testHandleTap_centralRegion_tapInsideCenter_isHitTested() {
        enableFeatureInManualMode()
        // 0.6 region on a 1080x1920 screen: center (540, 960), so a dead-center tap is inside.
        `when`(mockExperimentConfig.getDouble(
            Experiment.INFO_CARD_TAP_REGION_FRACTION,
            ObjectInfoTapHandler.DEFAULT_TAP_REGION_FRACTION))
            .thenReturn(0.6)
        `when`(mockHitTester.findObjectAtScreenPosition(540f, 960f, 1080, 1920))
            .thenReturn(testObjectInfo)

        val result = tapHandler.handleTap(540f, 960f, 1080, 1920)

        assertThat(result).isTrue()
        verify(mockListener).onObjectTapped(testObjectInfo)
    }

    @Test
    fun testHandleTap_centralRegion_tapNearCorner_isIgnoredWithoutHitTesting() {
        enableFeatureInManualMode()
        `when`(mockExperimentConfig.getDouble(
            Experiment.INFO_CARD_TAP_REGION_FRACTION,
            ObjectInfoTapHandler.DEFAULT_TAP_REGION_FRACTION))
            .thenReturn(0.6)

        // Top-left corner is well outside the centered 60% box.
        val result = tapHandler.handleTap(20f, 20f, 1080, 1920)

        assertThat(result).isFalse()
        verify(mockHitTester, never()).findObjectAtScreenPosition(20f, 20f, 1080, 1920)
    }

    @Test
    fun testHandleTap_centralRegion_fractionAtLeastOne_disablesRestriction() {
        enableFeatureInManualMode()
        `when`(mockExperimentConfig.getDouble(
            Experiment.INFO_CARD_TAP_REGION_FRACTION,
            ObjectInfoTapHandler.DEFAULT_TAP_REGION_FRACTION))
            .thenReturn(1.0)
        // Even a corner tap is hit-tested when the region covers the whole screen.
        `when`(mockHitTester.findObjectAtScreenPosition(20f, 20f, 1080, 1920))
            .thenReturn(testObjectInfo)

        val result = tapHandler.handleTap(20f, 20f, 1080, 1920)

        assertThat(result).isTrue()
        verify(mockListener).onObjectTapped(testObjectInfo)
    }
}
