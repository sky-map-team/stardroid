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
 * Every catalog name must normalize on this platform exactly as it did on the JVM (D126): the
 * catalog DB stores JVM-normalized names, so a name that differs here can never be found by
 * search. On the JVM this is a self-check; on iOS it is the real one. Gradle exports the corpus
 * (`:data:generator:exportNameCorpus`) and passes its path in [CORPUS_PATH_ENV].
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
                        // Show UTF-16 units: the forms often render identically (e.g. Hangul
                        // syllables vs. their decomposed jamo).
                        val here = NameNormalizer.normalize(name)
                        "  $name\n    JVM:  \"$jvm\" [${jvm.utf16Units()}]\n" +
                            "    here: \"$here\" [${here.utf16Units()}]"
                    },
            )
        }
    }

    private fun String.utf16Units(): String =
        map { it.code.toString(16).uppercase().padStart(4, '0') }.joinToString(" ")

    private companion object {
        const val CORPUS_PATH_ENV = "SKYMAP_NAME_CORPUS"
    }
}
