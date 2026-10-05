/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import kotlin.time.Duration.Companion.days

/** What this device has already done with each announcement, for [AnnouncementPolicy]. */
interface AnnouncementState {
    val seen: Flow<Map<String, SeenRecord>>

    suspend fun markShown(
        id: String,
        surface: Surface,
        now: Instant,
    )

    /** The user explicitly closed the message: hide it on every surface. */
    suspend fun dismiss(
        id: String,
        now: Instant,
    )

    /** Forget records for messages that left the payload more than [RETENTION] ago. */
    suspend fun prune(
        activeIds: Set<String>,
        now: Instant,
    )

    companion object {
        val RETENTION = 14.days
    }
}

/**
 * [AnnouncementState] as one JSON string in the shared settings DataStore:
 * `{"<id>":{"s":["notification"],"d":false,"t":<epochSeconds>}}`. A handful of tiny entries, so
 * a single preference beats a key per message, and `edit` keeps read-modify-write atomic.
 */
class DataStoreAnnouncementState(
    private val dataStore: DataStore<Preferences>,
) : AnnouncementState {
    override val seen: Flow<Map<String, SeenRecord>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { decode(it[KEY]).mapValues { (_, v) -> v.record } }
            .distinctUntilChanged()

    override suspend fun markShown(
        id: String,
        surface: Surface,
        now: Instant,
    ) = update { entries ->
        val old = entries[id]
        entries + (
            id to
                Entry(
                    (old?.record ?: SeenRecord()).let {
                        it.copy(shownOn = it.shownOn + surface)
                    },
                    old?.firstSeen ?: now,
                )
        )
    }

    override suspend fun dismiss(
        id: String,
        now: Instant,
    ) = update { entries ->
        val old = entries[id]
        entries + (id to Entry((old?.record ?: SeenRecord()).copy(dismissed = true), old?.firstSeen ?: now))
    }

    override suspend fun prune(
        activeIds: Set<String>,
        now: Instant,
    ) = update { entries ->
        entries.filterKeys { id ->
            id in activeIds || now - entries.getValue(id).firstSeen < AnnouncementState.RETENTION
        }
    }

    private suspend fun update(transform: (Map<String, Entry>) -> Map<String, Entry>) {
        dataStore.edit { prefs ->
            val before = decode(prefs[KEY])
            val after = transform(before)
            if (after != before) prefs[KEY] = encode(after)
        }
    }

    internal data class Entry(
        val record: SeenRecord,
        val firstSeen: Instant,
    )

    internal companion object {
        private val KEY = stringPreferencesKey("announcement_seen")

        fun encode(entries: Map<String, Entry>): String =
            buildJsonObject {
                for ((id, e) in entries) {
                    put(
                        id,
                        buildJsonObject {
                            putJsonArray("s") {
                                e.record.shownOn.forEach { add(JsonPrimitive(it.wire)) }
                            }
                            put("d", e.record.dismissed)
                            put("t", e.firstSeen.epochSeconds)
                        },
                    )
                }
            }.toString()

        /** Lenient: an unreadable value is treated as "nothing seen yet", never a crash. */
        fun decode(raw: String?): Map<String, Entry> {
            val root =
                try {
                    raw?.let { Json.parseToJsonElement(it) as? JsonObject }
                } catch (e: IllegalArgumentException) {
                    null
                } ?: return emptyMap()
            return buildMap {
                for ((id, value) in root) {
                    val obj = value as? JsonObject ?: continue
                    val surfaces =
                        (obj["s"] as? JsonArray)
                            .orEmpty()
                            .mapNotNull {
                                (it as? JsonPrimitive)?.contentOrNull?.let(
                                    Surface::fromWire,
                                )
                            }
                            .toSet()
                    val dismissed = (obj["d"] as? JsonPrimitive)?.booleanOrNull ?: false
                    val firstSeen =
                        Instant.fromEpochSeconds(
                            (obj["t"] as? JsonPrimitive)?.longOrNull ?: 0L,
                        )
                    put(id, Entry(SeenRecord(surfaces, dismissed), firstSeen))
                }
            }
        }
    }
}
