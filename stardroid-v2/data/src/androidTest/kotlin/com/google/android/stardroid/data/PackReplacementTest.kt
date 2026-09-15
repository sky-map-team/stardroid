/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pack lifecycle guarantees (catalog-and-schema.md): application is transactional per pack,
 * replacing one pack never touches another's rows, cross-pack references dangle gracefully,
 * and running layer flows pick up catalog changes via Room invalidation.
 */
@RunWith(AndroidJUnit4::class)
class PackReplacementTest {
    private lateinit var database: SkyMapDatabase
    private lateinit var repository: RoomCatalogRepository

    private val english = LocaleSpec("en")

    @Before
    fun createDb() =
        runTest {
            database =
                Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SkyMapDatabase::class.java,
                ).build()
            database.packDao().applyPack(FixtureCatalog.corePack(), FixtureCatalog.coreContents())
            database.packDao().applyPack(FixtureCatalog.extraPack(), FixtureCatalog.extraContents())
            repository = RoomCatalogRepository(database)
        }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun packs_listsInstalledPacksWithProvenance() =
        runTest {
            val packs = database.packDao().packs()

            assertThat(packs.map { it.id }).containsExactly("core", "extra").inOrder()
            assertThat(packs.single { it.id == "core" }.builtin).isTrue()
            assertThat(packs.single { it.id == "extra" }.builtin).isFalse()
        }

    @Test
    fun replacingPackSwapsItsContentWholesale() =
        runTest {
            // Version 2 of 'extra' renames Vega's entry and drops nothing else.
            val renamed =
                FixtureCatalog.extraContents().let { contents ->
                    contents.copy(
                        names =
                            contents.names.map {
                                if (it.name == "Vega") it.copy(name = "Wega") else it
                            },
                    )
                }

            database.packDao().applyPack(FixtureCatalog.extraPack(version = 2), renamed)

            assertThat(repository.searchByPrefix("wega", english, 10).single().id)
                .isEqualTo(CelestialObjectId("star/vega"))
            assertThat(repository.searchByPrefix("vega", english, 10)).isEmpty()
            assertThat(database.packDao().packs().single { it.id == "extra" }.version)
                .isEqualTo(2)
        }

    @Test
    fun replacingOnePackLeavesOthersUntouched() =
        runTest {
            database.packDao()
                .applyPack(FixtureCatalog.extraPack(version = 2), FixtureCatalog.extraContents())

            // Everything from 'core' is still there.
            assertThat(repository.searchByPrefix("sirius", english, 10)).isNotEmpty()
            assertThat(repository.figures(LayerKind.CONSTELLATIONS).first()).isNotEmpty()
            assertThat(repository.objectInfo(CelestialObjectId("dso/m31"), english)).isNotNull()
        }

    @Test
    fun failedApplyRollsBackLeavingPriorStateIntact() =
        runTest {
            val duplicateIds =
                FixtureCatalog.extraContents().let { contents ->
                    contents.copy(objects = contents.objects + contents.objects)
                }

            try {
                database.packDao().applyPack(FixtureCatalog.extraPack(version = 2), duplicateIds)
                throw AssertionError("applyPack should have failed on a duplicate object id")
            } catch (expected: SQLiteConstraintException) {
                // The whole transaction must roll back, insertions and deletions alike.
            }

            assertThat(repository.searchByPrefix("vega", english, 10)).isNotEmpty()
            assertThat(database.packDao().packs().single { it.id == "extra" }.version)
                .isEqualTo(1)
        }

    @Test
    fun removePackDeletesItsRowsAndDanglesIncomingLinks() =
        runTest {
            database.packDao().removePack("extra")

            assertThat(database.packDao().packs().map { it.id }).containsExactly("core")
            assertThat(repository.searchByPrefix("vega", english, 10)).isEmpty()
            val stars = repository.layerObjects(LayerKind.STARS, english).first()
            assertThat(stars.map { it.id.value }).doesNotContain("star/vega")

            // core's Jupiter → Vega chip disappears (dangling), the rest survive...
            val jupiter = repository.objectInfo(CelestialObjectId("planet/jupiter"), english)!!
            assertThat(jupiter.links.map { it.id.value }).containsExactly("moon/io")

            // ...and reinstalling the pack brings the link back to life.
            database.packDao()
                .applyPack(FixtureCatalog.extraPack(), FixtureCatalog.extraContents())
            val relinked = repository.objectInfo(CelestialObjectId("planet/jupiter"), english)!!
            assertThat(relinked.links.map { it.id.value })
                .containsExactly("moon/io", "star/vega")
                .inOrder()
        }

    @Test
    fun layerFlowReEmitsWhenPackChanges() =
        runTest {
            repository.layerObjects(LayerKind.STARS, english).test {
                assertThat(awaitItem().map { it.id.value }).contains("star/vega")

                database.packDao().removePack("extra")

                assertThat(awaitItem().map { it.id.value }).doesNotContain("star/vega")
                cancelAndIgnoreRemainingEvents()
            }
        }
}
