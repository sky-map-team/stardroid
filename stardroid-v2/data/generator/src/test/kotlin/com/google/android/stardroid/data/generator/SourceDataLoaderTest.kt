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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The `objects.json` overlay merge, exercised against a patched copy of the real
 * `source-data/` (path injected by the Gradle build).
 */
class SourceDataLoaderTest {
    private val sourceData =
        Path.of(
            requireNotNull(System.getProperty("skymap.sourceData")) {
                "System property 'skymap.sourceData' is not set — the Gradle test task passes it."
            },
        )

    private val gemini = "constellation/gemini"

    /** A copy of the real tree with [patch] applied to `objects.json`'s [gemini] entry. */
    private fun loadWithGeminiPatch(patch: Map<String, JsonPrimitive>): CatalogData {
        val dir = Files.createTempDirectory("source-data-test")
        try {
            sourceData.toFile().copyRecursively(dir.toFile())
            val objectsFile = dir.resolve("objects.json")
            val root = Json.parseToJsonElement(Files.readString(objectsFile)).jsonObject
            val objects = root.getValue("objects").jsonObject
            val patched = JsonObject(objects.getValue(gemini).jsonObject + patch)
            Files.writeString(
                objectsFile,
                Json.encodeToString(
                    JsonObject(root + ("objects" to JsonObject(objects + (gemini to patched)))),
                ),
            )
            return SourceDataLoader.load(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun CatalogData.gemini() = objects.single { it.id == gemini }

    /**
     * No positional catalog sets a search FOV, so an overlaid one is the only one there is —
     * dropping it would silently leave search unable to frame the constellation.
     */
    @Test
    fun `an overlaid search_fov reaches the constellation`() {
        val loaded = loadWithGeminiPatch(mapOf("search_fov" to JsonPrimitive(30.0)))
        assertThat(loaded.gemini().searchFov).isEqualTo(30.0)
    }

    /** iau.json owns constellation positions; the overlay must not be able to move them. */
    @Test
    fun `an overlaid position does not displace the positional catalog`() {
        val loaded =
            loadWithGeminiPatch(
                mapOf("ra" to JsonPrimitive(0.0), "dec" to JsonPrimitive(0.0)),
            )
        assertThat(loaded.gemini().ra).isWithin(1e-6).of(103.00154)
        assertThat(loaded.gemini().dec).isWithin(1e-6).of(24.82)
    }

    /** The shipped data declares no constellation FOV — the field stays opt-in. */
    @Test
    fun `constellations carry no search FOV by default`() {
        assertThat(SourceDataLoader.load(sourceData).gemini().searchFov).isNull()
    }

    /** The bundled pack ships v1's star set: magnitude < 5.6 (StarAttributeCalculator). */
    @Test
    fun `stars at or above the bundled magnitude cutoff are excluded`() {
        val loaded = SourceDataLoader.load(sourceData)
        val stars = loaded.objects.filter { it.id.startsWith("star/") }
        // The single exact-count parity pin; other tests derive their expectation from the
        // loader rather than repeating this hand-counted value.
        assertThat(stars).hasSize(3186)
        assertThat(
            stars.filter { it.magnitude!! >= SourceDataLoader.BUNDLED_STAR_MAX_MAGNITUDE },
        ).isEmpty()
    }

    /** Names of filtered-out stars are pruned rather than failing validation. */
    @Test
    fun `names of excluded faint stars are pruned`() {
        // p Eridani is magnitude 5.9 with a primary name in universal.csv.
        val loaded = SourceDataLoader.load(sourceData)
        assertThat(loaded.objects.filter { it.id == "star/p_eridani" }).isEmpty()
        assertThat(loaded.names.filter { it.objectId == "star/p_eridani" }).isEmpty()
    }

    /** Cards of filtered-out stars are pruned too, so card text can lead the bundle. */
    @Test
    fun `info cards for excluded faint stars are pruned`() {
        // R Coronae Borealis is magnitude 5.89 and carries a card in info_cards/en.json.
        val loaded = SourceDataLoader.load(sourceData)
        assertThat(loaded.objects.filter { it.id == "star/n25" }).isEmpty()
        assertThat(loaded.cards.filter { it.objectId == "star/n25" }).isEmpty()
    }

    /** The star-card prune must not loosen validation for other namespaces. */
    @Test
    fun `a dangling non-star info card still fails validation`() {
        val dir = Files.createTempDirectory("source-data-test")
        try {
            sourceData.toFile().copyRecursively(dir.toFile())
            val en = dir.resolve("info_cards/en.json")
            Files.writeString(
                en,
                Files.readString(en).replaceFirst(
                    "\"cards\": {",
                    "\"cards\": {\n  \"dso/does_not_exist\": { \"description\": \"x\" },",
                ),
            )
            val failure = runCatching { SourceDataLoader.load(dir) }.exceptionOrNull()
            assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
            assertThat(failure).hasMessageThat().contains("dso/does_not_exist")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    /** The star-name prune must not loosen validation for other namespaces. */
    @Test
    fun `a dangling non-star name still fails validation`() {
        val dir = Files.createTempDirectory("source-data-test")
        try {
            sourceData.toFile().copyRecursively(dir.toFile())
            val universal = dir.resolve("names/universal.csv")
            Files.writeString(
                universal,
                Files.readString(universal) + "dso/does_not_exist,Nowhere Nebula,1\n",
            )
            val failure =
                runCatching { SourceDataLoader.load(dir) }.exceptionOrNull()
            assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
            assertThat(failure).hasMessageThat().contains("dso/does_not_exist")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
