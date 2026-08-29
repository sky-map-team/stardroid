/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.generator

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

/**
 * Runs the generator against the real checked-in `source-data/` and the exported Room schema
 * (paths injected by the Gradle build) and verifies the catalog-and-schema.md guarantees:
 * deterministic byte-identical output, schema/identity-hash conformance, and the content
 * expectations the repository relies on.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogDbGeneratorTest {
    private val schemaPath = Path.of(System.getProperty("skymap.schemaJson")!!)
    private val sourceData = Path.of(System.getProperty("skymap.sourceData")!!)
    private lateinit var workDir: Path
    private lateinit var db: Path

    @BeforeAll
    fun generate() {
        workDir = Files.createTempDirectory("catalog-db-test")
        db = workDir.resolve("skymap.db")
        generateCatalogDb(schemaPath, sourceData, db)
    }

    @AfterAll
    fun cleanup() {
        workDir.toFile().deleteRecursively()
    }

    private fun <T> query(
        sql: String,
        read: (java.sql.ResultSet) -> T,
    ): T =
        DriverManager.getConnection("jdbc:sqlite:$db").use { conn ->
            conn.createStatement().use { st -> st.executeQuery(sql).use(read) }
        }

    private fun queryStrings(sql: String): List<String> =
        query(sql) { rs ->
            buildList { while (rs.next()) add(rs.getString(1)) }
        }

    private fun queryInt(sql: String): Int = query(sql) { it.getInt(1) }

    // --- reproducibility ---

    @Test
    fun `output is byte-identical across runs`() {
        val second = workDir.resolve("skymap-2.db")
        generateCatalogDb(schemaPath, sourceData, second)
        assertThat(Files.readAllBytes(second)).isEqualTo(Files.readAllBytes(db))
    }

    // --- schema conformance ---

    @Test
    fun `identity hash and user_version match the exported Room schema`() {
        val database =
            Json.parseToJsonElement(Files.readString(schemaPath))
                .jsonObject.getValue("database").jsonObject
        val expectedHash = database.getValue("identityHash").jsonPrimitive.content
        assertThat(queryStrings("SELECT identity_hash FROM room_master_table"))
            .containsExactly(expectedHash)
        assertThat(queryInt("PRAGMA user_version"))
            .isEqualTo(database.getValue("version").jsonPrimitive.content.toInt())
    }

    @Test
    fun `all schema tables and indices exist`() {
        val tables = queryStrings("SELECT name FROM sqlite_master WHERE type='table'")
        assertThat(tables).containsAtLeast(
            "pack", "object_type", "type_name", "celestial_object", "object_link",
            "object_name", "object_name_fts", "meteor_shower", "info_card", "figure",
            "figure_vertex", "room_master_table", "android_metadata",
        )
        val indices = queryStrings("SELECT name FROM sqlite_master WHERE type='index'")
        assertThat(indices).contains("index_object_name_name_normalized")
    }

    // --- content ---

    @Test
    fun `pack row is the builtin core pack with no timestamp`() {
        query("SELECT id, builtin, installed_at, version FROM pack") { rs ->
            assertThat(rs.next()).isTrue()
            assertThat(rs.getString(1)).isEqualTo("core")
            assertThat(rs.getInt(2)).isEqualTo(1)
            assertThat(rs.getLong(3)).isEqualTo(0L)
            assertThat(rs.getInt(4)).isEqualTo(1)
            assertThat(rs.next()).isFalse()
        }
    }

    @Test
    fun `stars and dsos and constellations are rendered layers`() {
        // v1's bundled star set (exact-count parity is pinned in SourceDataLoaderTest).
        val expectedStars =
            SourceDataLoader.load(sourceData).objects.count { it.id.startsWith("star/") }
        assertThat(queryInt("SELECT COUNT(*) FROM celestial_object WHERE layer_kind='stars'"))
            .isEqualTo(expectedStars)
        assertThat(queryInt("SELECT COUNT(*) FROM celestial_object WHERE layer_kind='deep_sky'"))
            .isAtLeast(130)
        assertThat(
            queryInt("SELECT COUNT(*) FROM celestial_object WHERE layer_kind='constellations'"),
        ).isEqualTo(89)
        assertThat(queryInt("SELECT COUNT(*) FROM figure")).isEqualTo(89)
        assertThat(queryInt("SELECT COUNT(*) FROM figure_vertex")).isAtLeast(900)
    }

    @Test
    fun `sirius carries the raw catalog magnitude`() {
        query(
            "SELECT layer_kind, type, ra, dec, magnitude FROM celestial_object " +
                "WHERE id='star/sirius'",
        ) { rs ->
            assertThat(rs.next()).isTrue()
            assertThat(rs.getString(1)).isEqualTo("stars")
            assertThat(rs.getString(2)).isEqualTo("star")
            assertThat(rs.getDouble(3)).isWithin(1e-6).of(101.25)
            assertThat(rs.getDouble(4)).isWithin(1e-6).of(-16.71)
            assertThat(rs.getDouble(5)).isWithin(1e-6).of(-1.43)
        }
    }

    @Test
    fun `ephemeris bodies store no position and no layer`() {
        query(
            "SELECT ra, dec, layer_kind, image_ref FROM celestial_object " +
                "WHERE id='planet/jupiter'",
        ) { rs ->
            assertThat(rs.next()).isTrue()
            rs.getDouble(1)
            assertThat(rs.wasNull()).isTrue()
            rs.getDouble(2)
            assertThat(rs.wasNull()).isTrue()
            assertThat(rs.getString(3)).isNull()
            assertThat(rs.getString(4)).isNotEmpty()
        }
    }

    @Test
    fun `moons resolve to their parent planet`() {
        assertThat(
            queryStrings(
                "SELECT parent_object_id FROM celestial_object WHERE id='moon/io'",
            ),
        ).containsExactly("planet/jupiter")
    }

    @Test
    fun `meteor showers are rendered with an activity sidecar row each`() {
        // Every shower is in the meteor-showers layer with a position and a search FOV…
        query(
            "SELECT COUNT(*), COUNT(ra), COUNT(search_fov) FROM celestial_object " +
                "WHERE layer_kind='meteor_showers'",
        ) { rs ->
            assertThat(rs.next()).isTrue()
            assertThat(rs.getInt(1)).isEqualTo(10)
            assertThat(rs.getInt(2)).isEqualTo(10)
            assertThat(rs.getInt(3)).isEqualTo(10)
        }
        // …and exactly one sidecar row, all of which join back to a shower object.
        assertThat(queryInt("SELECT COUNT(*) FROM meteor_shower")).isEqualTo(10)
        assertThat(
            queryInt(
                "SELECT COUNT(*) FROM meteor_shower s JOIN celestial_object o " +
                    "ON o.id = s.object_id WHERE o.layer_kind='meteor_showers'",
            ),
        ).isEqualTo(10)
        query(
            "SELECT active_from, peak, active_to, peak_zhr FROM meteor_shower " +
                "WHERE object_id='shower/perseids'",
        ) { rs ->
            assertThat(rs.next()).isTrue()
            assertThat(rs.getString(1)).isEqualTo("07-17")
            assertThat(rs.getString(2)).isEqualTo("08-13")
            assertThat(rs.getString(3)).isEqualTo("08-24")
            assertThat(rs.getInt(4)).isEqualTo(100)
        }
    }

    @Test
    fun `black holes have positions but are not rendered`() {
        query(
            "SELECT COUNT(*), COUNT(ra), COUNT(layer_kind) FROM celestial_object " +
                "WHERE type='black_hole'",
        ) { rs ->
            assertThat(rs.next()).isTrue()
            assertThat(rs.getInt(1)).isEqualTo(2)
            assertThat(rs.getInt(2)).isEqualTo(2)
            assertThat(rs.getInt(3)).isEqualTo(0)
        }
    }

    @Test
    fun `fts prefix match folds diacritics`() {
        // "Galaxia de Andrómeda" (es) must match an ASCII prefix query.
        val matches =
            queryStrings(
                "SELECT n.object_id FROM object_name n JOIN object_name_fts f " +
                    "ON n.rowid = f.docid WHERE object_name_fts MATCH 'andromeda*'",
            )
        assertThat(matches).contains("dso/m31")
    }

    @Test
    fun `name_normalized matches the shared NameNormalizer`() {
        assertThat(
            queryStrings(
                "SELECT name_normalized FROM object_name WHERE name='Galaxia de Andrómeda'",
            ),
        ).containsExactly("galaxia de andromeda")
    }

    @Test
    fun `universal designations live in the empty locale`() {
        assertThat(
            queryStrings(
                "SELECT locale FROM object_name WHERE name='NGC 224' AND object_id='dso/m31'",
            ),
        ).containsExactly("")
        // M2 has no proper name, so its designation is its primary name.
        assertThat(
            queryInt(
                "SELECT is_primary FROM object_name WHERE object_id='dso/m2' AND name='M2'",
            ),
        ).isEqualTo(1)
    }

    @Test
    fun `see also links keep display order`() {
        assertThat(
            queryStrings(
                "SELECT linked_id FROM object_link WHERE object_id='planet/jupiter' " +
                    "ORDER BY seq",
            ),
        ).containsExactly("moon/io", "moon/europa", "moon/ganymede", "moon/callisto")
            .inOrder()
    }

    @Test
    fun `info cards cover english plus translations`() {
        assertThat(
            queryInt("SELECT COUNT(*) FROM info_card WHERE locale='en'"),
        ).isEqualTo(339)
        assertThat(
            queryInt("SELECT COUNT(DISTINCT locale) FROM info_card"),
        ).isAtLeast(25)
    }

    @Test
    fun `every object type is in the shared vocabulary`() {
        assertThat(
            queryStrings(
                "SELECT DISTINCT type FROM celestial_object " +
                    "WHERE type NOT IN (SELECT code FROM object_type)",
            ),
        ).isEmpty()
        assertThat(queryStrings("SELECT parent_code FROM object_type WHERE code='galaxy.spiral'"))
            .containsExactly("galaxy")
    }

    // --- validation ---

    @Test
    fun `internally inconsistent pack data is rejected`() {
        val minimal =
            CatalogData(
                pack = PackRow("core", 1, null, null, builtin = true),
                types = listOf(TypeRow("star", null)),
                typeNames = emptyList(),
                objects = emptyList(),
                names = emptyList(),
                links = listOf(LinkRow("star/nowhere", "star/elsewhere", 0)),
                cards = emptyList(),
                figures = emptyList(),
                vertices = emptyList(),
            )
        val error = runCatching { minimal.validate() }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("star/nowhere")
    }
}
