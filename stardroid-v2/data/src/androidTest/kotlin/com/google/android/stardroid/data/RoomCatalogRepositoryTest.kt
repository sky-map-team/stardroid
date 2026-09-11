/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.math.RaDec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository behavior against an in-memory DB seeded with [FixtureCatalog] — locale fallback,
 * FTS prefix search and ranking, position resolution, and figure grouping
 * (catalog-and-schema.md test plan).
 */
@RunWith(AndroidJUnit4::class)
class RoomCatalogRepositoryTest {
    private lateinit var database: SkyMapDatabase
    private lateinit var repository: RoomCatalogRepository

    private val english = LocaleSpec("en")
    private val spanish = LocaleSpec("es")

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

    // --- layerObjects ---

    @Test
    fun layerObjects_returnsOnlyPositionedObjectsOfThatLayer() =
        runTest {
            val stars = repository.layerObjects(LayerKind.STARS, english).first()

            assertThat(stars.map { it.id.value }).containsExactly(
                "star/sirius",
                "star/rigel",
                "star/little-sirius",
                "star/altair",
                "star/alderamin",
                // From the second installed pack — both packs feed the same layer.
                "star/vega",
            )
        }

    @Test
    fun layerObjects_carriesSemanticFields() =
        runTest {
            val sirius =
                repository.layerObjects(LayerKind.STARS, english).first()
                    .single { it.id.value == "star/sirius" }

            assertThat(sirius.name).isEqualTo("Sirius")
            assertThat(sirius.magnitude).isEqualTo(-1.46)
            assertThat(sirius.colorIndex).isEqualTo(0.0)
            assertThat(sirius.type.isA(com.google.android.stardroid.catalog.TypeCode("star")))
                .isTrue()
            assertThat(sirius.position)
                .isEqualTo(RaDec(FixtureCatalog.SIRIUS_POS.first, FixtureCatalog.SIRIUS_POS.second))
        }

    @Test
    fun layerObjects_resolvesNamesThroughLocaleChain() =
        runTest {
            val m31InSpanish =
                repository.layerObjects(LayerKind.DEEP_SKY, spanish).first().single()
            val m31InGerman =
                repository.layerObjects(LayerKind.DEEP_SKY, LocaleSpec("de")).first().single()

            assertThat(m31InSpanish.name).isEqualTo("Galaxia de Andrómeda")
            // No German names in the fixture: falls back to English.
            assertThat(m31InGerman.name).isEqualTo("Andromeda Galaxy")
        }

    @Test
    fun layerObjects_prefersPrimaryNameWithinSameLocale() =
        runTest {
            val sirius =
                repository.layerObjects(LayerKind.STARS, english).first()
                    .single { it.id.value == "star/sirius" }

            // "Dog Star" (en, non-primary) must lose to "Sirius" (en, primary) regardless of
            // the order the database returns the candidate rows in.
            assertThat(sirius.name).isEqualTo("Sirius")
        }

    // --- meteorShowers ---

    @Test
    fun meteorShowers_carriesRadiantWindowAndLocalizedName() =
        runTest {
            val showers = repository.meteorShowers(english).first()

            val perseids = showers.single()
            assertThat(perseids.id.value).isEqualTo("shower/perseids")
            assertThat(perseids.name).isEqualTo("Perseids")
            val (ra, dec) = FixtureCatalog.PERSEIDS_POS
            assertThat(perseids.radiant).isEqualTo(RaDec(ra, dec))
            assertThat(perseids.activeFrom).isEqualTo(MonthDay(7, 17))
            assertThat(perseids.peak).isEqualTo(MonthDay(8, 13))
            assertThat(perseids.activeTo).isEqualTo(MonthDay(8, 24))
            assertThat(perseids.peakZhr).isEqualTo(100)
        }

    // --- figures ---

    @Test
    fun figures_groupsStrokesInOrder() =
        runTest {
            val figures = repository.figures(LayerKind.CONSTELLATIONS).first()

            val orion = figures.single()
            assertThat(orion.owner).isEqualTo(CelestialObjectId("constellation/orion"))
            assertThat(orion.strokes).hasSize(2)
            assertThat(orion.strokes[0]).containsExactly(
                RaDec(83.0, 5.0),
                RaDec(84.0, 6.0),
                RaDec(85.0, 7.0),
            ).inOrder()
            assertThat(orion.strokes[1]).containsExactly(
                RaDec(80.0, 0.0),
                RaDec(81.0, 1.0),
            ).inOrder()
        }

    @Test
    fun figures_filtersByCulture() =
        runTest {
            val other = repository.figures(LayerKind.CONSTELLATIONS, culture = "other").first()

            assertThat(other.single().strokes.single()).containsExactly(
                RaDec(70.0, 2.0),
                RaDec(71.0, 3.0),
            ).inOrder()
        }

    // --- searchByPrefix ---

    @Test
    fun search_wholeNamePrefixRanksAboveWordPrefix() =
        runTest {
            val hits = repository.searchByPrefix("sir", english, limit = 10)

            assertThat(hits.map { it.name })
                .containsExactly("Sirius", "Little Sirius")
                .inOrder()
        }

    @Test
    fun search_equalQualityHitsRankBrighterFirst() =
        runTest {
            val hits = repository.searchByPrefix("al", english, limit = 10)

            // Both whole-name-prefix primaries; Altair (0.76) outshines Alderamin (2.51).
            assertThat(hits.map { it.name })
                .containsExactly("Altair", "Alderamin")
                .inOrder()
        }

    @Test
    fun search_multiWordQueryMatchesWordPrefixes() =
        runTest {
            val hits = repository.searchByPrefix("andr gal", english, limit = 10)

            assertThat(hits.single().id).isEqualTo(CelestialObjectId("dso/m31"))
        }

    @Test
    fun search_foldsDiacriticsBothWays() =
        runTest {
            // Accent-free query matching the accented Spanish name...
            val bareQuery = repository.searchByPrefix("galaxia de andro", spanish, limit = 10)
            // ...and an accented query matching it too.
            val accentedQuery = repository.searchByPrefix("Andrómeda", spanish, limit = 10)

            assertThat(bareQuery.single().id).isEqualTo(CelestialObjectId("dso/m31"))
            assertThat(accentedQuery.single().id).isEqualTo(CelestialObjectId("dso/m31"))
        }

    @Test
    fun search_universalDesignationsMatchInEveryLocale() =
        runTest {
            val hits = repository.searchByPrefix("ngc 2", spanish, limit = 10)

            assertThat(hits.single().name).isEqualTo("NGC 224")
        }

    @Test
    fun search_localeOutsideChainDoesNotMatch() =
        runTest {
            // The Spanish name is invisible to an English request (chain en → "").
            val hits = repository.searchByPrefix("galaxia", english, limit = 10)

            assertThat(hits).isEmpty()
        }

    @Test
    fun search_oneHitPerObjectPreferringBestMatchedName() =
        runTest {
            // "gal" whole-name-prefixes the es name and word-prefixes the universal ones;
            // one hit, displaying the better match.
            val hits = repository.searchByPrefix("gal", spanish, limit = 10)

            assertThat(hits).hasSize(1)
            assertThat(hits.single().name).isEqualTo("Galaxia de Andrómeda")
        }

    @Test
    fun search_translatedObjectIsNotMatchableUnderTheNamesItShadows() =
        runTest {
            // "galaxy" is a word only the en name carries — the es name has "Galaxia".
            assertThat(repository.searchByPrefix("galaxy", english, limit = 10)).isNotEmpty()

            // In es the translation shadows the en rows, so the English name is neither
            // matchable nor displayable: a Spanish user gets no English completion.
            assertThat(repository.searchByPrefix("galaxy", spanish, limit = 10)).isEmpty()
        }

    @Test
    fun search_untranslatedObjectStillFallsBackToEnglish() =
        runTest {
            // star/sirius has no es rows, so en wins the chain and stays matchable in es.
            val hits = repository.searchByPrefix("dog st", spanish, limit = 10)

            assertThat(hits.map { it.name }).containsExactly("Dog Star")
        }

    @Test
    fun search_carriesSubtextFromLocaleChainBestCard() =
        runTest {
            val englishHit = repository.searchByPrefix("andromeda", english, limit = 10).single()
            val spanishHit = repository.searchByPrefix("andromeda", spanish, limit = 10).single()

            assertThat(englishHit.subtext).isEqualTo("Galaxy in Andromeda")
            assertThat(spanishHit.subtext).isEqualTo("Galaxia en Andrómeda")
        }

    @Test
    fun search_parentlessUnpositionedObjectYieldsCardOnlyHit() =
        runTest {
            val hit = repository.searchByPrefix("celestial eq", english, limit = 10).single()

            assertThat(hit.position).isNull()
            assertThat(hit.searchFovDeg).isNull()
        }

    @Test
    fun search_unpositionedObjectResolvesToParentPositionAndFov() =
        runTest {
            val io = repository.searchByPrefix("io", english, limit = 10).single()

            assertThat(io.name).isEqualTo("Io")
            assertThat(io.subtext).isEqualTo("Orbits Jupiter")
            assertThat(io.position)
                .isEqualTo(
                    RaDec(FixtureCatalog.JUPITER_POS.first, FixtureCatalog.JUPITER_POS.second),
                )
            // Io has no search FOV of its own; Jupiter's applies at Jupiter's position.
            assertThat(io.searchFovDeg).isEqualTo(2.0)
        }

    @Test
    fun search_respectsLimit() =
        runTest {
            val hits = repository.searchByPrefix("a", english, limit = 2)

            assertThat(hits).hasSize(2)
        }

    @Test
    fun search_ftsOperatorKeywordsAreLiteralTerms() =
        runTest {
            // Uppercase AND/OR/NOT are FTS4 operator keywords; as queries they must be treated
            // as ordinary prefix terms, not blow up the MATCH expression.
            val hits = repository.searchByPrefix("AND", english, limit = 10)

            assertThat(hits.single().name).isEqualTo("Andromeda Galaxy")
        }

    @Test
    fun search_blankQueryReturnsNothing() =
        runTest {
            assertThat(repository.searchByPrefix("   ", english, limit = 10)).isEmpty()
        }

    // --- objectInfo ---

    @Test
    fun objectInfo_unknownObjectIsNull() =
        runTest {
            assertThat(repository.objectInfo(CelestialObjectId("star/nonexistent"), english))
                .isNull()
        }

    @Test
    fun objectInfo_assemblesCardForLocale() =
        runTest {
            val info = repository.objectInfo(CelestialObjectId("dso/m31"), english)!!

            assertThat(info.name).isEqualTo("Andromeda Galaxy")
            assertThat(info.type.path).isEqualTo("galaxy.spiral")
            assertThat(info.description).isEqualTo("The nearest large spiral galaxy.")
            assertThat(info.funFact).isEqualTo("It will merge with the Milky Way.")
            assertThat(info.distance).isEqualTo("2.5 million light years")
            assertThat(info.imageRef).isEqualTo("m31")
            assertThat(info.imageCredit).isEqualTo("NASA")
            assertThat(info.magnitude).isEqualTo(3.44)
            assertThat(info.parent).isNull()
        }

    @Test
    fun objectInfo_localeFallbackIsWholeRow() =
        runTest {
            val info = repository.objectInfo(CelestialObjectId("dso/m31"), spanish)!!

            // The es card wins the chain whole-row: its null description is NOT backfilled
            // from the en card — cards stay coherent within one locale.
            assertThat(info.searchSubtext).isEqualTo("Galaxia en Andrómeda")
            assertThat(info.description).isNull()
        }

    @Test
    fun objectInfo_otherNamesAreSearchableAliasesExceptTheTitle() =
        runTest {
            val info = repository.objectInfo(CelestialObjectId("dso/m31"), english)!!

            // Title is the primary en name; the universal designations are the aliases a user
            // might have searched. The es name is out of the en chain, so it is not listed.
            assertThat(info.name).isEqualTo("Andromeda Galaxy")
            assertThat(info.otherNames).containsExactly("M31", "NGC 224").inOrder()
        }

    @Test
    fun objectInfo_otherNamesExcludeTheLocalesTheTranslationShadows() =
        runTest {
            val info = repository.objectInfo(CelestialObjectId("dso/m31"), spanish)!!

            // es wins the chain outright, so the en name is shadowed and never reaches a
            // Spanish card; only the universal designations join the es title, which is
            // itself excluded.
            assertThat(info.name).isEqualTo("Galaxia de Andrómeda")
            assertThat(info.otherNames).containsExactly("M31", "NGC 224").inOrder()
        }

    @Test
    fun objectInfo_otherNamesFallBackToEnglishForAnUntranslatedObject() =
        runTest {
            // star/sirius has no es rows, so en wins the chain and its alias is still shown.
            val info = repository.objectInfo(CelestialObjectId("star/sirius"), spanish)!!

            assertThat(info.name).isEqualTo("Sirius")
            assertThat(info.otherNames).containsExactly("Dog Star", "HD 48915").inOrder()
        }

    @Test
    fun objectInfo_otherNamesEmptyWhenOnlyTheTitleExists() =
        runTest {
            val info = repository.objectInfo(CelestialObjectId("star/rigel"), english)!!

            assertThat(info.name).isEqualTo("Rigel")
            assertThat(info.otherNames).isEmpty()
        }

    @Test
    fun objectInfo_cardlessObjectStillYieldsInfo() =
        runTest {
            val info = repository.objectInfo(CelestialObjectId("star/rigel"), english)!!

            assertThat(info.name).isEqualTo("Rigel")
            assertThat(info.description).isNull()
        }

    @Test
    fun objectInfo_childCarriesParentBreadcrumbAndPosition() =
        runTest {
            val io = repository.objectInfo(CelestialObjectId("moon/io"), english)!!

            assertThat(io.parent).isEqualTo(CelestialObjectId("planet/jupiter"))
            assertThat(io.position)
                .isEqualTo(
                    RaDec(FixtureCatalog.JUPITER_POS.first, FixtureCatalog.JUPITER_POS.second),
                )
            assertThat(io.searchSubtext).isEqualTo("Orbits Jupiter")
        }

    @Test
    fun objectInfo_linksAreOrderedChipsWithDanglingTargetsDropped() =
        runTest {
            val jupiter = repository.objectInfo(CelestialObjectId("planet/jupiter"), english)!!

            // moon/europa was never installed; the cross-pack star/vega link is live.
            assertThat(jupiter.links.map { it.id.value })
                .containsExactly("moon/io", "star/vega")
                .inOrder()
            val ioChip = jupiter.links.first()
            assertThat(ioChip.name).isEqualTo("Io")
            assertThat(ioChip.subtext).isEqualTo("Orbits Jupiter")
            // The chip re-aims the arrow like a search hit: Io resolves to Jupiter's position.
            assertThat(ioChip.position)
                .isEqualTo(
                    RaDec(FixtureCatalog.JUPITER_POS.first, FixtureCatalog.JUPITER_POS.second),
                )
        }

    // --- galleryItems ---

    @Test
    fun galleryItems_returnsOnlyObjectsWithImagesSortedByName() =
        runTest {
            val items = repository.galleryItems(english)

            assertThat(items.map { it.name })
                .containsExactly("Andromeda Galaxy", "Jupiter", "Sirius")
                .inOrder()
            assertThat(items.map { it.imageRef })
                .containsExactly("m31", "jupiter", "sirius")
                .inOrder()
        }

    @Test
    fun galleryItems_resolvesNamesThroughLocaleChain() =
        runTest {
            val items = repository.galleryItems(spanish)

            // m31 has a Spanish name; the others fall back to English. Order tracks the
            // resolved display names.
            assertThat(items.map { it.name })
                .containsExactly("Galaxia de Andrómeda", "Jupiter", "Sirius")
                .inOrder()
        }
}
