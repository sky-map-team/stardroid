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
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
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
        tapHandler = ObjectInfoTapHandler(mockPreferences, mockHitTester, mockAnalytics)
        tapHandler.setObjectTapListener(mockListener)
    }

    @Test
    fun testHandleTap_featureDisabled_returnsFalse() {
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY, false))
            .thenReturn(false)

        val result = tapHandler.handleTap(100f, 200f, 1080, 1920)

        assertThat(result).isFalse()
        verify(mockHitTester, never()).findObjectAtScreenPosition(
            100f, 200f, 1080, 1920)
    }

    @Test
    fun testHandleTap_autoMode_returnsFalse() {
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY, false))
            .thenReturn(true)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.AUTO_MODE_PREF_KEY, true))
            .thenReturn(true)

        val result = tapHandler.handleTap(100f, 200f, 1080, 1920)

        assertThat(result).isFalse()
        verify(mockHitTester, never()).findObjectAtScreenPosition(
            100f, 200f, 1080, 1920)
    }

    @Test
    fun testHandleTap_manualMode_objectFound_returnsTrue() {
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY, false))
            .thenReturn(true)
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
            ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY, false))
            .thenReturn(true)
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
            ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY, false))
            .thenReturn(true)

        assertThat(tapHandler.isFeatureEnabled()).isTrue()
    }

    @Test
    fun testIsFeatureEnabled_disabled() {
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY, false))
            .thenReturn(false)

        assertThat(tapHandler.isFeatureEnabled()).isFalse()
    }

    @Test
    fun testHandleTap_noListener_doesNotCrash() {
        tapHandler.setObjectTapListener(null)
        `when`(mockPreferences.getBoolean(
            ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY, false))
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
}
