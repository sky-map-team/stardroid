/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.gallery

import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.GalleryItem
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.layers.FakeCatalogRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class GalleryRepository(
        private val items: (LocaleSpec) -> List<GalleryItem>,
    ) : CatalogRepository by FakeCatalogRepository() {
        constructor(items: List<GalleryItem>) : this({ items })

        var requestedLocale: LocaleSpec? = null

        override suspend fun galleryItems(locale: LocaleSpec): List<GalleryItem> {
            requestedLocale = locale
            return items(locale)
        }
    }

    @Test
    fun items_loadFromTheCatalogForTheAppLocale() =
        runTest(dispatcher) {
            val catalogItems =
                listOf(
                    GalleryItem(CelestialObjectId("dso/m31"), "Andromeda Galaxy", "m31.webp"),
                    GalleryItem(CelestialObjectId("planet/jupiter"), "Jupiter", "jupiter.webp"),
                )
            val repository = GalleryRepository(catalogItems)
            val locale = LocaleSpec("es")

            val viewModel =
                GalleryViewModel(catalog = { repository }, locale = MutableStateFlow(locale))
            runCurrent()

            assertThat(viewModel.items.value).isEqualTo(catalogItems)
            assertThat(repository.requestedLocale).isEqualTo(locale)
        }

    @Test
    fun items_startEmptyBeforeTheCatalogAnswers() =
        runTest(dispatcher) {
            val viewModel =
                GalleryViewModel(
                    catalog = { GalleryRepository(emptyList()) },
                    locale = MutableStateFlow(LocaleSpec("en")),
                )

            assertThat(viewModel.items.value).isEmpty()
        }

    @Test
    fun items_reListWhenTheAppLanguageChanges() =
        runTest(dispatcher) {
            val m31 = CelestialObjectId("dso/m31")
            val repository =
                GalleryRepository { locale ->
                    val name =
                        if (locale.tag == "es") "Galaxia de Andrómeda" else "Andromeda Galaxy"
                    listOf(GalleryItem(m31, name, "m31.webp"))
                }
            val locale = MutableStateFlow(LocaleSpec("en"))

            val viewModel = GalleryViewModel(catalog = { repository }, locale = locale)
            runCurrent()
            assertThat(viewModel.items.value.single().name).isEqualTo("Andromeda Galaxy")

            // The view model outlives the activity recreation a language switch triggers.
            locale.value = LocaleSpec("es")
            runCurrent()

            assertThat(viewModel.items.value.single().name).isEqualTo("Galaxia de Andrómeda")
        }
}
