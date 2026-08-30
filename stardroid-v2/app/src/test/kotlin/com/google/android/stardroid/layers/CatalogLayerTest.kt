/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.catalog.CatalogObject
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.TypeCode
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.render.api.LayerId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogLayerTest {
    private val catalog = FakeCatalogRepository()
    private val locale = MutableStateFlow(LocaleSpec("en"))

    private fun TestScope.starsLayer() =
        CatalogLayer(
            id = LayerId("catalog/stars"),
            depth = 30,
            kind = LayerKind.STARS,
            catalog = catalog,
            locale = locale,
            mapping = CatalogLayers.StarsMapping,
            mapContext = UnconfinedTestDispatcher(testScheduler),
        )

    private fun star(
        id: String,
        name: String,
    ) = CatalogObject(
        id = CelestialObjectId(id),
        layerKind = LayerKind.STARS,
        type = TypeCode("star"),
        position = RaDec(88.8, 7.4),
        magnitude = 0.42,
        colorIndex = 1.85,
        name = name,
        nameIsPrimary = true,
        searchFovDeg = null,
    )

    @Test
    fun `emits a mapped scene and re-emits on catalog change`() =
        runTest {
            catalog.objectsByKind.getValue(LayerKind.STARS).value =
                listOf(star("star/betelgeuse", "Betelgeuse"))

            val scenes = collectInBackground(starsLayer().scenes())

            assertThat(scenes).hasSize(1)
            assertThat(scenes.single().labels.single().text).isEqualTo("Betelgeuse")

            catalog.objectsByKind.getValue(LayerKind.STARS).value =
                listOf(star("star/betelgeuse", "Betelgeuse"), star("star/rigel", "Rigel"))

            assertThat(scenes).hasSize(2)
            assertThat(scenes.last().points).hasSize(2)
        }

    @Test
    fun `locale change restarts the catalog query with the new locale`() =
        runTest {
            collectInBackground(starsLayer().scenes())
            assertThat(catalog.requestedLocales).containsExactly(LocaleSpec("en"))

            locale.value = LocaleSpec("de")

            assertThat(catalog.requestedLocales)
                .containsExactly(LocaleSpec("en"), LocaleSpec("de"))
                .inOrder()
        }

    @Test
    fun `layers without figures never query the figures flow`() =
        runTest {
            collectInBackground(starsLayer().scenes())

            assertThat(catalog.figuresRequests).isEqualTo(0)
        }

    @Test
    fun `constellation scenes combine objects and figures`() =
        runTest {
            val layer =
                CatalogLayer(
                    id = LayerId("catalog/constellations"),
                    depth = 10,
                    kind = LayerKind.CONSTELLATIONS,
                    catalog = catalog,
                    locale = locale,
                    mapping = CatalogLayers.ConstellationsMapping,
                    mapContext = UnconfinedTestDispatcher(testScheduler),
                )
            catalog.objectsByKind.getValue(LayerKind.CONSTELLATIONS).value =
                listOf(
                    CatalogObject(
                        id = CelestialObjectId("constellation/orion"),
                        layerKind = LayerKind.CONSTELLATIONS,
                        type = TypeCode("constellation"),
                        position = RaDec(83.7, 3.6),
                        magnitude = null,
                        colorIndex = null,
                        name = "Orion",
                        nameIsPrimary = true,
                        searchFovDeg = null,
                    ),
                )
            catalog.figuresByKind.getValue(LayerKind.CONSTELLATIONS).value =
                listOf(
                    Figure(
                        owner = CelestialObjectId("constellation/orion"),
                        strokes = listOf(listOf(RaDec(83.0, -0.3), RaDec(84.1, -1.2))),
                    ),
                )

            val scenes = collectInBackground(layer.scenes())

            assertThat(catalog.figuresRequests).isEqualTo(1)
            assertThat(scenes.last().lines).hasSize(1)
            assertThat(scenes.last().labels.single().text).isEqualTo("Orion")
        }

    /**
     * Collects [flow] for the rest of the test on an unconfined dispatcher, so emissions land
     * in the returned list as soon as they happen (the coroutines-test flow-collection idiom).
     */
    private fun <T> TestScope.collectInBackground(flow: Flow<T>): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { items += it }
        }
        return items
    }
}
