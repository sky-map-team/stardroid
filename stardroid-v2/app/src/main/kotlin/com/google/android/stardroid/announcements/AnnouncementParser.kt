/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Parses the Remote Config `announcements` value. Lenient by design: this is hand-authored
 * JSON read by millions of devices, so a malformed message is dropped on its own and a
 * malformed document yields nothing — never an exception.
 */
object AnnouncementParser {
    /** The payload schema version this build understands; other versions are ignored. */
    const val SCHEMA_VERSION = 1

    /** Messages kept after parsing, newest `start` first. */
    const val MAX_MESSAGES = 3

    fun parse(json: String): List<Announcement> {
        if (json.isBlank()) return emptyList()
        val root =
            try {
                Json.parseToJsonElement(json) as? JsonObject
            } catch (e: IllegalArgumentException) {
                null
            } ?: return emptyList()
        if (root["v"].long() != SCHEMA_VERSION.toLong()) return emptyList()
        return (root["messages"] as? JsonArray)
            .orEmpty()
            .mapNotNull { parseMessage(it) }
            .distinctBy { it.id }
            .sortedByDescending { it.start }
            .take(MAX_MESSAGES)
    }

    private fun parseMessage(element: JsonElement): Announcement? {
        val obj = element as? JsonObject ?: return null
        val id = obj["id"].string()?.takeIf { it.isNotBlank() } ?: return null
        val start = obj["start"].instant() ?: return null
        val end = obj["end"].instant() ?: return null
        if (end <= start) return null
        val surfaces =
            (obj["surfaces"] as? JsonArray)
                .orEmpty()
                .mapNotNull { it.string()?.let(Surface::fromWire) }
                .toSet()
        if (surfaces.isEmpty()) return null
        val text = parseText(obj["text"]).takeIf { it.isNotEmpty() } ?: return null
        return Announcement(
            id = id,
            start = start,
            end = end,
            surfaces = surfaces,
            minVersion = obj["min_version"].long() ?: 0L,
            text = text,
            action = parseAction(obj["action"]),
        )
    }

    private fun parseText(element: JsonElement?): Map<String, AnnouncementText> {
        val obj = element as? JsonObject ?: return emptyMap()
        return buildMap {
            for ((tag, value) in obj) {
                val entry = value as? JsonObject ?: continue
                val title = entry["title"].string()?.takeIf { it.isNotBlank() } ?: continue
                val body = entry["body"].string().orEmpty()
                // LocaleSpec lowercases and uses '-', so normalize keys the same way.
                put(tag.trim().replace('_', '-').lowercase(), AnnouncementText(title, body))
            }
        }
    }

    private fun parseAction(element: JsonElement?): AnnouncementAction {
        val obj = element as? JsonObject ?: return AnnouncementAction.OpenSky
        val arg = obj["arg"].string()?.takeIf { it.isNotBlank() }
        return when (obj["type"].string()) {
            "search" -> arg?.let { AnnouncementAction.Search(it) } ?: AnnouncementAction.OpenSky
            else -> AnnouncementAction.OpenSky
        }
    }

    private fun JsonElement?.string(): String? =
        (this as? JsonPrimitive)?.takeIf {
            it.isString
        }?.contentOrNull

    private fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.longOrNull

    private fun JsonElement?.instant(): Instant? =
        string()?.let {
            try {
                Instant.parse(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
}
