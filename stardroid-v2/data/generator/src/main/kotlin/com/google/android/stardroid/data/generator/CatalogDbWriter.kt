/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.generator

import com.google.android.stardroid.catalog.NameNormalizer
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types

/**
 * Writes a [CatalogData] into a fresh SQLite file matching the exported Room schema.
 *
 * The output is byte-for-byte deterministic (the F-Droid reproducible-build requirement,
 * catalog-and-schema.md): a fresh file, a fixed page size, a pinned sqlite-jdbc version,
 * stable row ordering, and no wall-clock values (`installed_at` is 0 — "shipped with the
 * app", not an install event).
 */
object CatalogDbWriter {
    fun write(
        schema: RoomSchema,
        catalog: CatalogData,
        out: Path,
    ) {
        Files.deleteIfExists(out)
        out.parent?.let { Files.createDirectories(it) }
        DriverManager.getConnection("jdbc:sqlite:$out").use { conn ->
            conn.createStatement().use { st ->
                st.execute("PRAGMA page_size = 4096")
                // No crash-safety needed for a build artifact; avoids journal side-files.
                st.execute("PRAGMA journal_mode = MEMORY")
            }
            conn.autoCommit = false
            conn.createStatement().use { st ->
                // The framework adds this table on first open if missing; creating it here
                // keeps the shipped asset identical to what a device would settle on.
                st.executeUpdate("CREATE TABLE android_metadata (locale TEXT)")
                st.executeUpdate("INSERT INTO android_metadata (locale) VALUES ('en_US')")
                schema.createStatements.forEach(st::executeUpdate)
                // Triggers before inserts: object_name rows then FTS-index themselves.
                schema.triggers.forEach(st::executeUpdate)
                schema.setupQueries.forEach(st::executeUpdate)
            }
            insertRows(conn, catalog)
            conn.commit()
            // Back to autocommit first: with it off, the pragma would start a fresh
            // transaction that nothing ever commits, silently leaving the version at 0.
            conn.autoCommit = true
            conn.createStatement().use { st ->
                st.execute("PRAGMA user_version = ${schema.version}")
            }
        }
    }

    private fun insertRows(
        conn: Connection,
        catalog: CatalogData,
    ) {
        conn.batch(
            "INSERT INTO pack (id, version, license, source_url, installed_at, builtin) " +
                "VALUES (?, ?, ?, ?, 0, ?)",
            listOf(catalog.pack),
        ) { p ->
            setString(1, p.id)
            setInt(2, p.version)
            setNullableString(3, p.license)
            setNullableString(4, p.sourceUrl)
            setInt(5, if (p.builtin) 1 else 0)
        }
        conn.batch(
            "INSERT INTO object_type (code, parent_code) VALUES (?, ?)",
            catalog.types.sortedBy { it.code },
        ) { t ->
            setString(1, t.code)
            setNullableString(2, t.parentCode)
        }
        conn.batch(
            "INSERT INTO type_name (code, locale, name) VALUES (?, ?, ?)",
            catalog.typeNames.sortedWith(compareBy({ it.code }, { it.locale })),
        ) { t ->
            setString(1, t.code)
            setString(2, t.locale)
            setString(3, t.name)
        }
        conn.batch(
            "INSERT INTO celestial_object (id, pack_id, layer_kind, type, ra, dec, magnitude, " +
                "color_index, search_fov, parent_object_id, image_ref) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            catalog.objects.sortedBy { it.id },
        ) { o ->
            setString(1, o.id)
            setString(2, catalog.pack.id)
            setNullableString(3, o.layerKind)
            setString(4, o.type)
            setNullableDouble(5, o.ra)
            setNullableDouble(6, o.dec)
            setNullableDouble(7, o.magnitude)
            setNullableDouble(8, o.colorIndex)
            setNullableDouble(9, o.searchFov)
            setNullableString(10, o.parentObjectId)
            setNullableString(11, o.imageRef)
        }
        conn.batch(
            "INSERT INTO meteor_shower (object_id, active_from, peak, active_to, peak_zhr) " +
                "VALUES (?, ?, ?, ?, ?)",
            catalog.showers.sortedBy { it.objectId },
        ) { s ->
            setString(1, s.objectId)
            setString(2, s.activeFrom)
            setString(3, s.peak)
            setString(4, s.activeTo)
            setInt(5, s.peakZhr)
        }
        // Insertion order assigns object_name rowids, so it must be stable too.
        conn.batch(
            "INSERT INTO object_name (object_id, locale, name, name_normalized, is_primary) " +
                "VALUES (?, ?, ?, ?, ?)",
            catalog.names.sortedWith(
                compareBy({ it.objectId }, { it.locale }, { !it.isPrimary }, { it.name }),
            ),
        ) { n ->
            setString(1, n.objectId)
            setString(2, n.locale)
            setString(3, n.name)
            setString(4, NameNormalizer.normalize(n.name))
            setInt(5, if (n.isPrimary) 1 else 0)
        }
        conn.batch(
            "INSERT INTO object_link (object_id, linked_id, seq) VALUES (?, ?, ?)",
            catalog.links.sortedWith(compareBy({ it.objectId }, { it.seq })),
        ) { l ->
            setString(1, l.objectId)
            setString(2, l.linkedId)
            setInt(3, l.seq)
        }
        conn.batch(
            "INSERT INTO info_card (object_id, locale, description, fun_fact, distance, size, " +
                "mass, spectral_class, image_credit, search_subtext) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            catalog.cards.sortedWith(compareBy({ it.objectId }, { it.locale })),
        ) { c ->
            setString(1, c.objectId)
            setString(2, c.locale)
            setNullableString(3, c.description)
            setNullableString(4, c.funFact)
            setNullableString(5, c.distance)
            setNullableString(6, c.size)
            setNullableString(7, c.mass)
            setNullableString(8, c.spectralClass)
            setNullableString(9, c.imageCredit)
            setNullableString(10, c.searchSubtext)
        }
        conn.batch(
            "INSERT INTO figure (id, owner_object_id, layer_kind, kind, culture, pack_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            catalog.figures.sortedBy { it.id },
        ) { f ->
            setString(1, f.id)
            setString(2, f.ownerObjectId)
            setString(3, f.layerKind)
            setString(4, f.kind)
            setString(5, f.culture)
            setString(6, catalog.pack.id)
        }
        conn.batch(
            "INSERT INTO figure_vertex (figure_id, stroke, seq, ra, dec) VALUES (?, ?, ?, ?, ?)",
            catalog.vertices.sortedWith(compareBy({ it.figureId }, { it.stroke }, { it.seq })),
        ) { v ->
            setString(1, v.figureId)
            setInt(2, v.stroke)
            setInt(3, v.seq)
            setDouble(4, v.ra)
            setDouble(5, v.dec)
        }
    }

    private fun <T> Connection.batch(
        sql: String,
        rows: List<T>,
        bind: PreparedStatement.(T) -> Unit,
    ) {
        prepareStatement(sql).use { st ->
            for (row in rows) {
                st.bind(row)
                st.addBatch()
            }
            st.executeBatch()
        }
    }

    private fun PreparedStatement.setNullableString(
        index: Int,
        value: String?,
    ) = if (value == null) setNull(index, Types.VARCHAR) else setString(index, value)

    private fun PreparedStatement.setNullableDouble(
        index: Int,
        value: Double?,
    ) = if (value == null) setNull(index, Types.DOUBLE) else setDouble(index, value)
}
