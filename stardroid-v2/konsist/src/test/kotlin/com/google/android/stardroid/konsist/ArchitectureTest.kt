/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Enforces the pure/Android boundary and the inward-only dependency rule (D20).
 *
 * The structural guarantee is that pure modules apply `skymap.pure-kotlin` (a `kotlin("jvm")`
 * module with no Android SDK on the classpath), so `import android.*` cannot even compile.
 * This test is the belt-and-braces gate that also catches a module accidentally switched to an
 * Android plugin, and documents the rule as an executable spec.
 */
class ArchitectureTest {
    private val pureModuleSource =
        // Leading `(?:.*/)?` (not `.*/`) so the gate matches whether Konsist yields absolute or
        // repo-relative paths.
        Regex("""(?:.*/)?(core/(math|astronomy|catalog)|render/api|data/generator)/src/.*\.kt$""")

    private fun pureModuleFiles() =
        Konsist.scopeFromProject().files
            // Normalize separators so the gate also works on Windows checkouts.
            .filter { pureModuleSource.matches(it.path.replace('\\', '/')) }

    @Test
    fun `architecture gate actually scans pure module files`() {
        // Guards against a vacuous pass if the path filter ever stops matching (e.g. a layout
        // change): the two checks below are only meaningful if they have files to inspect.
        assertTrue(pureModuleFiles().isNotEmpty()) {
            "Architecture gate found no pure-module sources to scan — check pureModuleSource."
        }
    }

    @Test
    fun `pure modules do not import the Android framework`() {
        pureModuleFiles().assertFalse { file ->
            file.hasImport { import ->
                import.name.startsWith("android.") ||
                    import.name.startsWith("androidx.") ||
                    // Catch any Android-only library — Google (Play Services, Material) and
                    // third-party Android variants (kotlinx.coroutines.android, rxandroid, …) —
                    // while still allowing our own `com.google.android.stardroid.*` packages.
                    (
                        (import.name.contains(".android.") || import.name.endsWith(".android")) &&
                            !import.name.startsWith("com.google.android.stardroid.")
                    )
            }
        }
    }

    @Test
    fun `pure modules do not depend on Android-only modules`() {
        // The :app module's namespace is the package root `com.google.android.stardroid` (no
        // `.app` segment), so denylisting specific Android-module packages would silently miss
        // app classes such as `com.google.android.stardroid.activities.*`. Allow-list instead:
        // every in-project import from a pure module must resolve to one of the pure packages.
        val allowedPureModulePackages =
            listOf(
                "com.google.android.stardroid.math.",
                "com.google.android.stardroid.astronomy.",
                "com.google.android.stardroid.catalog.",
                "com.google.android.stardroid.render.api.",
            )
        pureModuleFiles().assertFalse { file ->
            file.hasImport { import ->
                import.name.startsWith("com.google.android.stardroid.") &&
                    allowedPureModulePackages.none { import.name.startsWith(it) }
            }
        }
    }
}
