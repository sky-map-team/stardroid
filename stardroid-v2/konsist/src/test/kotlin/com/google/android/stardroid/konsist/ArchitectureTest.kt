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
 * Enforces the pure/Android boundary, the inward-only dependency rule (D20), and the
 * `:render:gles3` sibling-backend boundary (render-gles3.md §2).
 *
 * The structural guarantee for the pure/Android checks is that pure modules apply
 * `skymap.pure-kotlin` (a `kotlin("jvm")` module with no Android SDK on the classpath), so
 * `import android.*` cannot even compile. Those tests are the belt-and-braces gate that also
 * catches a module accidentally switched to an Android plugin, and document the rule as an
 * executable spec. `:render:gles3` has no such structural backstop — both it and `:render:gles1`
 * are ordinary Android library modules, so nothing stops one importing the other's internals by
 * hand, and this gate is the only thing that would catch it.
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

    private val gles3ModuleSource =
        Regex("""(?:.*/)?render/gles3/src/.*\.kt$""")

    // The one allowed caller (render-gles3.md §2 — the backend is selected at MainActivity's
    // construction site): every :app source set (main, debug, gms, fdroid, test, androidTest).
    private val appModuleSource =
        Regex("""(?:.*/)?app/src/.*\.kt$""")

    private fun gles3ModuleFiles() =
        Konsist.scopeFromProject().files.filter {
            gles3ModuleSource.matches(it.path.replace('\\', '/'))
        }

    // Everything that could import gles3's internals if the boundary ever slipped: every file
    // outside both :render:gles3 itself and :app. Built as an explicit exclusion of the two
    // known-allowed regexes rather than one combined pattern with a negative lookahead — a
    // lookahead only constrains the characters immediately at that position, and backtracking
    // on the greedy `.*` prefix can hop past a literal "app/" segment and still satisfy it from
    // a different split point (confirmed: an earlier version of this rule let
    // `app/src/main/kotlin/.../RendererBackends.kt` slip through, because `.*` backtracked to
    // stop one directory short of "app/", leaving "app/" itself outside the lookahead's view
    // while a *later* "/src/" in the same path still satisfied the rest of the pattern).
    private fun nonGles3AppFiles() =
        Konsist.scopeFromProject().files.filterNot { file ->
            val path = file.path.replace('\\', '/')
            gles3ModuleSource.matches(path) || appModuleSource.matches(path)
        }

    @Test
    fun `gles3 boundary gate actually scans gles3 and app files`() {
        assertTrue(gles3ModuleFiles().isNotEmpty()) {
            "gles3 boundary gate found no :render:gles3 sources to scan — check gles3ModuleSource."
        }
        assertTrue(nonGles3AppFiles().isNotEmpty()) {
            "gles3 boundary gate found no non-gles3 sources to scan — check the exclusion regexes."
        }
    }

    @Test
    fun `render gles3 does not import render gles1`() {
        // The two backends are siblings behind the same SkyRenderer contract, not layers — one
        // reaching into the other's internals (e.g. to avoid duplicating a formula) would be
        // exactly the coupling the sibling-module design was meant to avoid.
        gles3ModuleFiles().assertFalse { file ->
            file.hasImport { import ->
                import.name.startsWith("com.google.android.stardroid.render.gles1.")
            }
        }
    }

    @Test
    fun `nothing outside app depends on render gles3`() {
        // render-gles3.md §2: "nothing may depend on :render:gles3 except :app". Only :app is
        // meant to choose a backend; every other module — including :render:gles1 — must stay
        // ignorant of the sibling's existence.
        nonGles3AppFiles().assertFalse { file ->
            file.hasImport { import ->
                import.name.startsWith("com.google.android.stardroid.render.gles3.")
            }
        }
    }
}
