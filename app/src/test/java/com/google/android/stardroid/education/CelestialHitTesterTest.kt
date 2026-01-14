// Copyright 2024 Google Inc.
//
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

import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.layers.LayerManager
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.search.SearchResult
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [CelestialHitTester].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CelestialHitTesterTest {

    @Mock
    private lateinit var mockAstronomerModel: AstronomerModel

    @Mock
    private lateinit var mockLayerManager: LayerManager

    @Mock
    private lateinit var mockObjectInfoRegistry: ObjectInfoRegistry

    @Mock
    private lateinit var mockPointing: AstronomerModel.Pointing

    @Mock
    private lateinit var mockSearchResult: SearchResult

    private lateinit var hitTester: CelestialHitTester

    private val sunInfo = ObjectInfo("sun", "Sun", "Our star", "Very hot")
    private val marsInfo = ObjectInfo("mars", "Mars", "Red planet", "Has volcanoes")

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        // Default pointing: looking straight ahead (+X direction)
        `when`(mockPointing.lineOfSight).thenReturn(Vector3(1f, 0f, 0f))
        `when`(mockPointing.perpendicular).thenReturn(Vector3(0f, 0f, 1f))
        `when`(mockAstronomerModel.pointing).thenReturn(mockPointing)
        `when`(mockAstronomerModel.fieldOfView).thenReturn(90f)

        // Default registry setup
        `when`(mockObjectInfoRegistry.supportedObjectIds).thenReturn(setOf("sun", "mars"))
        `when`(mockObjectInfoRegistry.getSearchName("sun")).thenReturn("Sun")
        `when`(mockObjectInfoRegistry.getSearchName("mars")).thenReturn("Mars")
        `when`(mockObjectInfoRegistry.getInfo("sun")).thenReturn(sunInfo)
        `when`(mockObjectInfoRegistry.getInfo("mars")).thenReturn(marsInfo)

        hitTester = CelestialHitTester(
            mockAstronomerModel,
            mockLayerManager,
            mockObjectInfoRegistry
        )
    }

    @Test
    fun testFindObjectAtScreenPosition_noObjectsFound() {
        `when`(mockLayerManager.searchByObjectName("Sun")).thenReturn(emptyList())
        `when`(mockLayerManager.searchByObjectName("Mars")).thenReturn(emptyList())

        val result = hitTester.findObjectAtScreenPosition(
            540f, 960f, 1080, 1920
        )

        assertThat(result).isNull()
    }

    @Test
    fun testFindObjectAtScreenPosition_objectFoundWithinThreshold() {
        // Sun is directly in front (center of screen)
        val sunCoords = Vector3(1f, 0f, 0f)
        `when`(mockSearchResult.coords()).thenReturn(sunCoords)
        `when`(mockLayerManager.searchByObjectName("Sun"))
            .thenReturn(listOf(mockSearchResult))
        `when`(mockLayerManager.searchByObjectName("Mars")).thenReturn(emptyList())

        val result = hitTester.findObjectAtScreenPosition(
            540f, 960f, 1080, 1920
        )

        assertThat(result).isEqualTo(sunInfo)
    }

    @Test
    fun testFindObjectAtScreenPosition_closestObjectSelected() {
        // Both objects visible, but Sun is closer to tap point
        val sunCoords = Vector3(1f, 0.01f, 0f)  // Slightly off center
        val marsCoords = Vector3(1f, 0.1f, 0f)  // Further off center

        val sunSearchResult = org.mockito.Mockito.mock(SearchResult::class.java)
        val marsSearchResult = org.mockito.Mockito.mock(SearchResult::class.java)

        `when`(sunSearchResult.coords()).thenReturn(sunCoords)
        `when`(marsSearchResult.coords()).thenReturn(marsCoords)

        `when`(mockLayerManager.searchByObjectName("Sun"))
            .thenReturn(listOf(sunSearchResult))
        `when`(mockLayerManager.searchByObjectName("Mars"))
            .thenReturn(listOf(marsSearchResult))

        val result = hitTester.findObjectAtScreenPosition(
            540f, 960f, 1080, 1920
        )

        // Should find Sun since it's closer
        assertThat(result).isEqualTo(sunInfo)
    }

    @Test
    fun testFindObjectAtScreenPosition_objectTooFarFromTap() {
        // Object is visible but far from tap point (more than 5 degrees)
        val farCoords = Vector3(0f, 1f, 0f)  // 90 degrees from center
        `when`(mockSearchResult.coords()).thenReturn(farCoords)
        `when`(mockLayerManager.searchByObjectName("Sun"))
            .thenReturn(listOf(mockSearchResult))
        `when`(mockLayerManager.searchByObjectName("Mars")).thenReturn(emptyList())

        val result = hitTester.findObjectAtScreenPosition(
            540f, 960f, 1080, 1920
        )

        assertThat(result).isNull()
    }

    @Test
    fun testFindObjectAtScreenPosition_nullSearchResultsIgnored() {
        `when`(mockLayerManager.searchByObjectName("Sun"))
            .thenReturn(listOf(null, mockSearchResult))
        `when`(mockLayerManager.searchByObjectName("Mars")).thenReturn(emptyList())

        val sunCoords = Vector3(1f, 0f, 0f)
        `when`(mockSearchResult.coords()).thenReturn(sunCoords)

        val result = hitTester.findObjectAtScreenPosition(
            540f, 960f, 1080, 1920
        )

        assertThat(result).isEqualTo(sunInfo)
    }

    @Test
    fun testFindObjectAtScreenPosition_missingSearchName() {
        `when`(mockObjectInfoRegistry.getSearchName("sun")).thenReturn(null)

        val marsCoords = Vector3(1f, 0f, 0f)
        `when`(mockSearchResult.coords()).thenReturn(marsCoords)
        `when`(mockLayerManager.searchByObjectName("Mars"))
            .thenReturn(listOf(mockSearchResult))

        val result = hitTester.findObjectAtScreenPosition(
            540f, 960f, 1080, 1920
        )

        // Should find Mars since Sun has no search name
        assertThat(result).isEqualTo(marsInfo)
    }
}
