/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * End-to-end proof of the 4c pipeline: the `skymap.db` asset produced by
 * `:data:generateCatalogDb` opens through [SkyMapDatabaseFactory]'s `createFromAsset` (which
 * makes Room validate the generator's schema and identity hash on a real device) and answers
 * repository queries with the harvested v1 content.
 */
@RunWith(AndroidJUnit4::class)
class BundledCatalogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: SkyMapDatabase
    private lateinit var repository: RoomCatalogRepository

    private val english = LocaleSpec("en")
    private val french = LocaleSpec("fr")

    @Before
    fun openBundledDatabase() {
        context.deleteDatabase(SkyMapDatabaseFactory.DATABASE_NAME)
        database = SkyMapDatabaseFactory.create(context)
        repository = RoomCatalogRepository(database)
    }

    @After
    fun cleanup() {
        // Guarded so a failure in @Before surfaces itself, not an uninitialized-property error.
        if (::database.isInitialized) {
            database.close()
        }
        context.deleteDatabase(SkyMapDatabaseFactory.DATABASE_NAME)
    }

    @Test
    fun starsLayerServesTheV1StarSet() =
        runTest {
            val stars = repository.layerObjects(LayerKind.STARS, english).first()
            // v1's bundled cutoff: magnitude < 5.6 (generator BUNDLED_STAR_MAX_MAGNITUDE).
            // Deliberately duplicates SourceDataLoaderTest's exact pin: this one gates the
            // packaged skymap.db asset end-to-end, catching a stale or mis-generated bundle
            // that the JVM-side loader test cannot see.
            assertThat(stars.size).isEqualTo(3186)
            assertThat(stars.filter { it.magnitude!! >= 5.6 }).isEmpty()
            val sirius = stars.single { it.id.value == "star/sirius" }
            assertThat(sirius.name).isEqualTo("Sirius")
            assertThat(sirius.magnitude).isEqualTo(-1.43)
            assertThat(sirius.nameIsPrimary).isTrue()
        }

    @Test
    fun bayerDesignationsAreSearchableButNeverTheDisplayedPrimary() =
        runTest {
            // Deneb by its Bayer and Flamsteed forms; the layer still shows "Deneb".
            assertThat(repository.searchByPrefix("alpha cyg", english, 10).map { it.id.value })
                .contains("star/deneb")
            assertThat(repository.searchByPrefix("50 cyg", english, 10).map { it.id.value })
                .contains("star/deneb")
            val deneb =
                repository.layerObjects(LayerKind.STARS, english).first()
                    .single { it.id.value == "star/deneb" }
            assertThat(deneb.name).isEqualTo("Deneb")
            assertThat(deneb.nameIsPrimary).isTrue()

            // Gamma Cassiopeiae has no IAU proper name: searchable, but its designation is
            // secondary so it must not become a map label (CatalogLayers gates on primary).
            assertThat(repository.searchByPrefix("gamma cas", english, 10).map { it.id.value })
                .contains("star/j005700+604312")
            val gammaCas =
                repository.layerObjects(LayerKind.STARS, english).first()
                    .single { it.id.value == "star/j005700+604312" }
            assertThat(gammaCas.nameIsPrimary).isFalse()
        }

    @Test
    fun constellationFiguresCoverTheWholeSky() =
        runTest {
            val figures = repository.figures(LayerKind.CONSTELLATIONS).first()
            assertThat(figures).hasSize(89)
            val orion =
                figures.single { it.owner == CelestialObjectId("constellation/orion") }
            assertThat(orion.strokes).isNotEmpty()
        }

    @Test
    fun searchFindsSiriusByPrefix() =
        runTest {
            val hits = repository.searchByPrefix("siri", english, 10)
            assertThat(hits.map { it.id.value }).contains("star/sirius")
        }

    @Test
    fun searchResolvesMoonsToTheirPlanetlessPosition() =
        runTest {
            // Io has no stored position and neither does Jupiter (ephemeris-positioned):
            // the hit surfaces with a null position for the ViewModel to resolve (D-seam).
            val io =
                repository.searchByPrefix(
                    "io",
                    english,
                    25,
                ).single { it.id.value == "moon/io" }
            assertThat(io.position).isNull()
            assertThat(io.subtext).isEqualTo("Moon of Jupiter")
        }

    @Test
    fun localizedSearchMatchesDiacriticFreePrefixes() =
        runTest {
            // Index-side folding: the ASCII prefix must match the accented Spanish name.
            val hits = repository.searchByPrefix("androm", LocaleSpec("es"), 10)
            assertThat(hits.map { it.id.value }).contains("dso/m31")
            // Display name follows the best-matching name: a Spanish whole-name-prefix
            // match surfaces the harvested translation.
            val m31 =
                repository.searchByPrefix("galax", LocaleSpec("es"), 10)
                    .single { it.id.value == "dso/m31" }
            assertThat(m31.name).isEqualTo("Galaxia de Andrómeda")
        }

    @Test
    fun frenchLocaleIsNeverShownEnglishNamesOrSubtexts() =
        runTest {
            // The suggestion subtitle is card prose, not a datum, so it has to be translated
            // rather than copied from English — this read "Moon of Neptune" (D108).
            val triton =
                repository.searchByPrefix("triton", french, 10).single {
                    it.id.value == "moon/triton"
                }
            assertThat(triton.subtext).isEqualTo("Lune de Neptune")

            // "Triangulum Galaxy" lived in universal.csv, which joins every locale and is
            // excluded from translation, so it reached French untranslated *and* untranslatable.
            val m33 = repository.objectInfo(CelestialObjectId("dso/m33"), french)!!
            assertThat(m33.name).isEqualTo("Galaxie du Triangle")
            assertThat(m33.otherNames).containsExactly("M33", "NGC 598").inOrder()

            // The English name is now shadowed by the translation: not matchable, not shown.
            assertThat(repository.searchByPrefix("triangulum", french, 10).map { it.id.value })
                .doesNotContain("dso/m33")
            // The designation still reaches it, in every locale.
            assertThat(repository.searchByPrefix("m33", french, 10).map { it.id.value })
                .contains("dso/m33")
        }

    @Test
    fun designationsStayMatchableInATranslatedLocale() =
        runTest {
            val japanese = LocaleSpec("ja")

            // ja has its own name for Achernar, so the whole-object rule (D108) shadows the
            // English rows. Designations live in the universal locale, which joins the winner
            // instead of competing with it, so they survive that shadowing in every locale.
            val info = repository.objectInfo(CelestialObjectId("star/achernar"), japanese)!!
            assertThat(info.name).isEqualTo("アケルナル")
            assertThat(info.otherNames).contains("Alpha Eridani")

            val hit =
                repository.searchByPrefix("alpha eri", japanese, 10)
                    .single { it.id.value == "star/achernar" }
            // A hit displays the name that matched, not the card title — so searching a
            // designation shows the designation, exactly as searching "M31" does. The card
            // reached through it is still Japanese.
            assertThat(hit.name).isEqualTo("Alpha Eridani")
            assertThat(repository.objectInfo(hit.id, japanese)!!.name).isEqualTo("アケルナル")
        }

    @Test
    fun infoCardComesBackLocalizedWithLinks() =
        runTest {
            val info = repository.objectInfo(CelestialObjectId("planet/jupiter"), english)!!
            assertThat(info.name).isEqualTo("Jupiter")
            assertThat(info.description).isNotEmpty()
            assertThat(info.imageRef).isNotEmpty()
            assertThat(info.links.map { it.id.value })
                .containsExactly("moon/io", "moon/europa", "moon/ganymede", "moon/callisto")
                .inOrder()
        }

    @Test
    fun cardlessObjectsStillYieldInfo() =
        runTest {
            // Most stars carry no card row; the flow must still work (D33 contract).
            val faintStar =
                repository.layerObjects(LayerKind.STARS, english)
                    .first()
                    .first { it.id.value.startsWith("star/j") }
            val info = repository.objectInfo(faintStar.id, english)
            assertThat(info).isNotNull()
            assertThat(info!!.description).isNull()
        }
}
