/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import kotlinx.datetime.Instant

/**
 * On-disk home for the element sets and the fetch state that guards them. Durable storage, never
 * a cache the OS may evict: losing the TLE silently kills the feature for offline users.
 * Element sets are stored verbatim, so the network and cache paths share one parser.
 */
class TleStore(
    private val files: TextFiles,
) {
    /** The cached element text, or null if absent or unreadable — never throws (UI threads call this). */
    fun readElements(): String? = runCatching { files.read(ELEMENTS_FILE) }.getOrNull()

    /**
     * Replaces the cached element sets atomically, so a failed write keeps the previous good copy
     * (half a TLE can parse as a plausible wrong orbit). Throws if they could not be persisted;
     * the repository turns that into [FetchOutcome.StorageFailure].
     */
    fun writeElements(text: String) = files.replace(ELEMENTS_FILE, text)

    /** The persisted fetch state; a missing or corrupt file reads as "never fetched". */
    fun readState(): SatelliteFetchState =
        runCatching {
            val fields =
                files.read(STATE_FILE)
                    ?.lines()
                    ?.mapNotNull { line ->
                        line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
                    }
                    ?.toMap()
                    ?: return SatelliteFetchState()

            SatelliteFetchState(
                consecutiveFailures = fields[KEY_FAILURES]?.toIntOrNull() ?: 0,
                circuitOpenUntil = fields[KEY_OPEN_UNTIL]?.toInstantOrNull(),
                lastAttempt = fields[KEY_LAST_ATTEMPT]?.toInstantOrNull(),
                lastSuccess = fields[KEY_LAST_SUCCESS]?.toInstantOrNull(),
                elementsWrittenAt = fields[KEY_ELEMENTS_WRITTEN_AT]?.toInstantOrNull(),
                lastModified = fields[KEY_LAST_MODIFIED]?.takeIf { it.isNotEmpty() },
                lastStatusCode = fields[KEY_LAST_STATUS]?.toIntOrNull(),
            )
        }.getOrElse { SatelliteFetchState() }

    /**
     * Hand-rolled `key=value`: `:data` has no JSON library at runtime, and `Last-Modified` never
     * contains a newline or `=`.
     */
    fun writeState(state: SatelliteFetchState) {
        val text =
            buildString {
                appendLine("$KEY_FAILURES=${state.consecutiveFailures}")
                state.circuitOpenUntil?.let { appendLine("$KEY_OPEN_UNTIL=$it") }
                state.lastAttempt?.let { appendLine("$KEY_LAST_ATTEMPT=$it") }
                state.lastSuccess?.let { appendLine("$KEY_LAST_SUCCESS=$it") }
                state.elementsWrittenAt?.let { appendLine("$KEY_ELEMENTS_WRITTEN_AT=$it") }
                state.lastModified?.let { appendLine("$KEY_LAST_MODIFIED=$it") }
                state.lastStatusCode?.let { appendLine("$KEY_LAST_STATUS=$it") }
            }
        files.replace(STATE_FILE, text)
    }

    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    private companion object {
        const val ELEMENTS_FILE = "elements.tle"
        const val STATE_FILE = "fetch-state.txt"

        const val KEY_FAILURES = "consecutiveFailures"
        const val KEY_OPEN_UNTIL = "circuitOpenUntil"
        const val KEY_LAST_ATTEMPT = "lastAttempt"
        const val KEY_LAST_SUCCESS = "lastSuccess"
        const val KEY_ELEMENTS_WRITTEN_AT = "elementsWrittenAt"
        const val KEY_LAST_MODIFIED = "lastModified"
        const val KEY_LAST_STATUS = "lastStatusCode"
    }
}

/**
 * The few named text files [TleStore] keeps, on the platform's file system: a directory of them,
 * in practice ([DirectoryTextFiles] on each platform).
 */
interface TextFiles {
    /**
     * The named file's text, or null if there is no such file. An unreadable file may throw or
     * read as null; [TleStore] treats both as absent.
     */
    fun read(name: String): String?

    /**
     * Replaces the named file's text atomically: written beside it and moved into place, so a
     * kill mid-write leaves the previous text rather than half of the new one. Throws if the text
     * could not be persisted, leaving the previous text in place.
     */
    fun replace(
        name: String,
        text: String,
    )
}
