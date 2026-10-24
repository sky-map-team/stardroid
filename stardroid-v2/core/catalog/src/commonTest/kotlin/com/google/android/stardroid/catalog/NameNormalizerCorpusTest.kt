/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.catalog

import com.google.android.stardroid.testing.assertThat
import com.google.android.stardroid.testing.environmentVariable
import com.google.android.stardroid.testing.readTextFile
import kotlin.test.Test
import kotlin.test.fail

/**
 * Every catalog name must normalize on this platform exactly as it did on the JVM (D126).
 *
 * The bundled catalog DB stores names normalized by the JVM, at generation time; a search
 * normalizes the query with *this* platform's [NameNormalizer] and matches it against them. The
 * code is common, but NFD, `Regex` and `lowercase` all run on per-platform Unicode tables, so a
 * name that normalizes differently here is one this platform's search can never find. On the
 * JVM this re-checks the JVM against itself; on iOS it is the real check.
 *
 * The corpus is every distinct name in `source-data/` with its JVM form, exported by
 * `:data:generator:exportNameCorpus`; Gradle runs that first and passes the path in
 * [CORPUS_PATH_ENV].
 */
class NameNormalizerCorpusTest {
    @Test
    fun `every catalog name normalizes exactly as it did on the JVM`() {
        val path =
            environmentVariable(CORPUS_PATH_ENV)
                ?: fail("$CORPUS_PATH_ENV is unset: run this test through Gradle, which exports it")
        val corpus =
            readTextFile(path).lineSequence().filter { it.isNotEmpty() }.map { line ->
                val fields = line.split('\t')
                check(fields.size == 2) { "malformed corpus line: $line" }
                fields[0] to fields[1]
            }.toList()
        assertThat(corpus).isNotEmpty()

        val mismatches = corpus.filter { (name, jvm) -> NameNormalizer.normalize(name) != jvm }
        if (mismatches.isNotEmpty()) {
            fail(
                "${mismatches.size} of ${corpus.size} catalog names normalize differently " +
                    "from the JVM:\n" +
                    mismatches.take(20).joinToString("\n") { (name, jvm) ->
                        // Code points too: the two forms often render identically (e.g. Hangul
                        // syllables vs. their decomposed jamo).
                        val here = NameNormalizer.normalize(name)
                        "  $name\n    JVM:  \"$jvm\" [${jvm.codePoints()}]\n" +
                            "    here: \"$here\" [${here.codePoints()}]"
                    },
            )
        }
    }

    private fun String.codePoints(): String =
        map { it.code.toString(16).uppercase().padStart(4, '0') }.joinToString(" ")

    private companion object {
        const val CORPUS_PATH_ENV = "SKYMAP_NAME_CORPUS"
    }
}
