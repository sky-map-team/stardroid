/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.generator

import java.nio.file.Path

/**
 * Entry point for the `:data:generateCatalogDb` Gradle task: compiles `source-data/` into the
 * bundled catalog database (catalog-and-schema.md, D32/D34).
 */
fun main(args: Array<String>) {
    require(args.size == 3) {
        "usage: generator <room-schema.json> <source-data-dir> <output-db>"
    }
    val (schemaPath, sourceDir, out) = args.map { Path.of(it) }
    generateCatalogDb(schemaPath, sourceDir, out)
}

fun generateCatalogDb(
    schemaPath: Path,
    sourceDir: Path,
    out: Path,
) {
    val schema = parseRoomSchema(schemaPath)
    val catalog = SourceDataLoader.load(sourceDir)
    CatalogDbWriter.write(schema, catalog, out)
    println(
        "Wrote $out: ${catalog.objects.size} objects, ${catalog.names.size} names, " +
            "${catalog.figures.size} figures, ${catalog.cards.size} cards",
    )
}
