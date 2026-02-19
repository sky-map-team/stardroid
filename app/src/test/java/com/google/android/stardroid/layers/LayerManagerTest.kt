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

package com.google.android.stardroid.layers

import android.content.SharedPreferences
import com.google.android.stardroid.renderer.RendererController
import com.google.android.stardroid.search.SearchResult
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [LayerManager].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LayerManagerTest {

    @Mock
    private lateinit var mockPreferences: SharedPreferences

    @Mock
    private lateinit var mockRenderer: RendererController

    @Mock
    private lateinit var mockLayer1: Layer

    @Mock
    private lateinit var mockLayer2: Layer

    @Mock
    private lateinit var mockSearchResult1: SearchResult

    @Mock
    private lateinit var mockSearchResult2: SearchResult

    private lateinit var layerManager: LayerManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        // Set up default mock behavior
        whenever(mockLayer1.preferenceId).thenReturn("layer1_pref")
        whenever(mockLayer1.layerName).thenReturn("Layer 1")
        whenever(mockLayer2.preferenceId).thenReturn("layer2_pref")
        whenever(mockLayer2.layerName).thenReturn("Layer 2")

        // Default: layers are visible
        whenever(mockPreferences.getBoolean(eq("layer1_pref"), any())).thenReturn(true)
        whenever(mockPreferences.getBoolean(eq("layer2_pref"), any())).thenReturn(true)

        layerManager = LayerManager(mockPreferences)
    }

    @Test
    fun addLayer_addsToInternalList() {
        layerManager.addLayer(mockLayer1)

        // Verify by calling initialize and checking layer.initialize is called
        layerManager.initialize()
        verify(mockLayer1).initialize()
    }

    @Test
    fun initialize_callsInitializeOnAllLayers() {
        layerManager.addLayer(mockLayer1)
        layerManager.addLayer(mockLayer2)

        layerManager.initialize()

        verify(mockLayer1).initialize()
        verify(mockLayer2).initialize()
    }

    @Test
    fun registerWithRenderer_registersAllLayers() {
        layerManager.addLayer(mockLayer1)
        layerManager.addLayer(mockLayer2)

        layerManager.registerWithRenderer(mockRenderer)

        verify(mockLayer1).registerWithRenderer(mockRenderer)
        verify(mockLayer2).registerWithRenderer(mockRenderer)
    }

    @Test
    fun registerWithRenderer_setsVisibilityFromPreferences() {
        whenever(mockPreferences.getBoolean(eq("layer1_pref"), any())).thenReturn(true)
        whenever(mockPreferences.getBoolean(eq("layer2_pref"), any())).thenReturn(false)

        layerManager.addLayer(mockLayer1)
        layerManager.addLayer(mockLayer2)
        layerManager.registerWithRenderer(mockRenderer)

        verify(mockLayer1).setVisible(true)
        verify(mockLayer2).setVisible(false)
    }

    @Test
    fun onSharedPreferenceChanged_updatesLayerVisibility() {
        layerManager.addLayer(mockLayer1)

        whenever(mockPreferences.getBoolean(eq("layer1_pref"), any())).thenReturn(false)
        layerManager.onSharedPreferenceChanged(mockPreferences, "layer1_pref")

        verify(mockLayer1).setVisible(false)
    }

    @Test
    fun onSharedPreferenceChanged_ignoresUnrelatedKeys() {
        layerManager.addLayer(mockLayer1)

        layerManager.onSharedPreferenceChanged(mockPreferences, "unrelated_key")

        // setVisible should not be called (only from registerWithRenderer)
        verify(mockLayer1, org.mockito.Mockito.never()).setVisible(any())
    }

    @Test
    fun searchByObjectName_searchesVisibleLayersOnly() {
        whenever(mockPreferences.getBoolean(eq("layer1_pref"), any())).thenReturn(true)
        whenever(mockPreferences.getBoolean(eq("layer2_pref"), any())).thenReturn(false)
        whenever(mockLayer1.searchByObjectName("Mars")).thenReturn(listOf(mockSearchResult1))
        whenever(mockLayer2.searchByObjectName("Mars")).thenReturn(listOf(mockSearchResult2))

        layerManager.addLayer(mockLayer1)
        layerManager.addLayer(mockLayer2)

        val results = layerManager.searchByObjectName("Mars")

        assertThat(results).containsExactly(mockSearchResult1)
        verify(mockLayer1).searchByObjectName("Mars")
        verify(mockLayer2, org.mockito.Mockito.never()).searchByObjectName(any())
    }

    @Test
    fun searchByObjectName_aggregatesResultsFromMultipleLayers() {
        whenever(mockLayer1.searchByObjectName("star")).thenReturn(listOf(mockSearchResult1))
        whenever(mockLayer2.searchByObjectName("star")).thenReturn(listOf(mockSearchResult2))

        layerManager.addLayer(mockLayer1)
        layerManager.addLayer(mockLayer2)

        val results = layerManager.searchByObjectName("star")

        assertThat(results).containsExactly(mockSearchResult1, mockSearchResult2)
    }

    @Test
    fun searchByObjectName_returnsEmptyForNoMatches() {
        whenever(mockLayer1.searchByObjectName("nonexistent")).thenReturn(emptyList())

        layerManager.addLayer(mockLayer1)

        val results = layerManager.searchByObjectName("nonexistent")

        assertThat(results).isEmpty()
    }

    @Test
    fun getObjectNamesMatchingPrefix_searchesVisibleLayersOnly() {
        whenever(mockPreferences.getBoolean(eq("layer1_pref"), any())).thenReturn(true)
        whenever(mockPreferences.getBoolean(eq("layer2_pref"), any())).thenReturn(false)
        whenever(mockLayer1.getObjectNamesMatchingPrefix("Ma")).thenReturn(setOf("Mars", "Maat Mons"))
        whenever(mockLayer2.getObjectNamesMatchingPrefix("Ma")).thenReturn(setOf("Magellanic Cloud"))

        layerManager.addLayer(mockLayer1)
        layerManager.addLayer(mockLayer2)

        val results = layerManager.getObjectNamesMatchingPrefix("Ma")

        // Results should only include layer1's results
        assertThat(results.map { it.query }).containsExactly("Mars", "Maat Mons")
        verify(mockLayer1).getObjectNamesMatchingPrefix("Ma")
        verify(mockLayer2, org.mockito.Mockito.never()).getObjectNamesMatchingPrefix(any())
    }

    @Test
    fun getObjectNamesMatchingPrefix_aggregatesResults() {
        whenever(mockLayer1.getObjectNamesMatchingPrefix("M")).thenReturn(setOf("Mars"))
        whenever(mockLayer2.getObjectNamesMatchingPrefix("M")).thenReturn(setOf("Moon"))

        layerManager.addLayer(mockLayer1)
        layerManager.addLayer(mockLayer2)

        val results = layerManager.getObjectNamesMatchingPrefix("M")

        assertThat(results.map { it.query }).containsExactly("Mars", "Moon")
    }

    @Test
    fun getObjectNamesMatchingPrefix_includesLayerName() {
        whenever(mockLayer1.getObjectNamesMatchingPrefix("Ma")).thenReturn(setOf("Mars"))

        layerManager.addLayer(mockLayer1)

        val results = layerManager.getObjectNamesMatchingPrefix("Ma")

        assertThat(results).hasSize(1)
        val result = results.first()
        assertThat(result.query).isEqualTo("Mars")
        assertThat(result.origin).isEqualTo("Layer 1")
    }

    @Test
    fun multipleLayersWithSameName_bothReturned() {
        // Two layers might have objects with the same name (e.g., "Saturn" in planets and moons)
        whenever(mockLayer1.getObjectNamesMatchingPrefix("S")).thenReturn(setOf("Saturn"))
        whenever(mockLayer2.getObjectNamesMatchingPrefix("S")).thenReturn(setOf("Saturn"))

        layerManager.addLayer(mockLayer1)
        layerManager.addLayer(mockLayer2)

        val results = layerManager.getObjectNamesMatchingPrefix("S")

        // Should have two results - one from each layer
        assertThat(results).hasSize(2)
        assertThat(results.map { it.origin }).containsExactly("Layer 1", "Layer 2")
    }
}
