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
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Entry point for the `:data:generator:exportNameCorpus` Gradle task: writes every distinct
 * catalog name with its JVM [NameNormalizer] form, one `name<TAB>normalized` line each, sorted.
 *
 * The catalog DB is normalized here, on the JVM, but searched on every platform, where the
 * query goes through that platform's own [NameNormalizer]. `:core:catalog`'s
 * `NameNormalizerCorpusTest` re-normalizes this corpus on each target and fails on any
 * disagreement, since a disagreement is a name that platform's search can never find (D115).
 */
object NameCorpusExport {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "usage: NameCorpusExport <source-data-dir> <output-file>" }
        exportNameCorpus(Path.of(args[0]), Path.of(args[1]))
    }
}

fun exportNameCorpus(
    sourceDir: Path,
    out: Path,
) {
    val names = SourceDataLoader.load(sourceDir).names.map { it.name }.distinct().sorted()
    names.forEach { name ->
        require(name.none { it == '\t' || it == '\n' || it == '\r' }) {
            "name '$name' cannot be written as a corpus line"
        }
    }
    out.parent?.createDirectories()
    out.writeText(names.joinToString("") { "$it\t${NameNormalizer.normalize(it)}\n" })
    println("Wrote $out: ${names.size} distinct names")
}
