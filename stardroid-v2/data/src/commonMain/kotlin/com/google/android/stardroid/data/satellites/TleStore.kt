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
 * On-disk home for the element sets and for the fetch state that guards them.
 *
 * **Durable storage, never a cache directory** (`filesDir` on Android, not `cacheDir`). The OS may
 * evict a cache at any moment, and losing the TLE silently kills the feature for anyone offline —
 * the exact users who most need a cached copy. This is small, durable, user-owned data.
 *
 * The element sets are stored **as received, verbatim**. The two-line format is already compact
 * and line-oriented, so re-serialising to JSON or Room would gain nothing and would mean the
 * network and cache paths exercise two parsers instead of one.
 */
class TleStore(
    private val files: TextFiles,
) {
    /**
     * The cached element-set text, or null if there is none **or it cannot be read**.
     *
     * Deliberately total: this is reached from [SatelliteElementsRepository.current], which UI code
     * calls on any thread, so an unreadable file has to degrade to "we have nothing" rather than
     * throw. The empty state already exists and says something honest; a crash while opening the
     * sky map would not be an improvement on it.
     */
    fun readElements(): String? = runCatching { files.read(ELEMENTS_FILE) }.getOrNull()

    /**
     * Replaces the cached element sets.
     *
     * Atomically ([TextFiles.replace]), so a kill mid-write cannot leave a truncated element set
     * behind — half a TLE parses as garbage or, worse, as a plausible wrong orbit. A failed
     * replace therefore leaves the *previous* good copy in place, which is the right outcome.
     *
     * Throws if the elements could not be persisted — a full or failing filesystem, not a
     * programming error (an `IOException` on Android). Callers run in a background worker and
     * must handle it rather than let it escape; the repository turns it into
     * [FetchOutcome.StorageFailure].
     */
    fun writeElements(text: String) = files.replace(ELEMENTS_FILE, text)

    /**
     * The persisted fetch state, or a fresh one if there is none or it cannot be read.
     *
     * A corrupt state file degrades to "never fetched" rather than throwing. That is the
     * conservative direction: the worst case is one extra request after the minimum interval, and
     * an app that cannot start because its backoff bookkeeping is unparseable would be worse than
     * the problem it is guarding against.
     */
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
     * Persists [state].
     *
     * A hand-rolled `key=value` format rather than JSON: `:data` carries no JSON library on its
     * runtime classpath (`kotlinx-serialization-json` is a build-time tool for `:data:generator`
     * only), and six scalar fields do not justify adding one. `Last-Modified` is an HTTP date and
     * contains no newlines or `=`, so the format is unambiguous for every value stored here.
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
     * The named file's text, or null if there is no such file. May throw if it exists but cannot
     * be read; [TleStore] treats that as absent.
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
