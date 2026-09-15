/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.generator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/**
 * The DDL extracted from a Room exported-schema JSON (`data/schemas/`). Executing exactly
 * Room's own statements — identity hash included, via [setupQueries] — is what makes the
 * generated file `createFromAsset`-compatible with zero drift risk: a schema change regenerates
 * the JSON, and this parser picks it up with no generator change.
 */
data class RoomSchema(
    val version: Int,
    /** Table and index CREATE statements, in Room's entity order. */
    val createStatements: List<String>,
    /** FTS external-content sync triggers; created before inserts so they index them. */
    val triggers: List<String>,
    /** `room_master_table` creation + identity-hash insert. */
    val setupQueries: List<String>,
)

fun parseRoomSchema(path: Path): RoomSchema {
    val db =
        Json.parseToJsonElement(Files.readString(path))
            .jsonObject.getValue("database").jsonObject
    val creates = mutableListOf<String>()
    val triggers = mutableListOf<String>()
    for (entity in db.getValue("entities").jsonArray) {
        val obj = entity.jsonObject
        val table = obj.getValue("tableName").jsonPrimitive.content

        fun expand(sql: String) = sql.replace("\${TABLE_NAME}", table)
        creates += expand(obj.getValue("createSql").jsonPrimitive.content)
        obj["indices"]?.jsonArray?.forEach {
            creates += expand(it.jsonObject.getValue("createSql").jsonPrimitive.content)
        }
        obj["contentSyncTriggers"]?.jsonArray?.forEach {
            triggers += it.jsonPrimitive.content
        }
    }
    // Views would need their own CREATE handling; fail loudly rather than silently drop them
    // if one is ever added (the identity hash would still match, hiding the omission).
    require(db["views"]?.jsonArray.isNullOrEmpty()) {
        "schema declares database views, which this generator does not support yet"
    }
    return RoomSchema(
        version = db.getValue("version").jsonPrimitive.int,
        createStatements = creates,
        triggers = triggers,
        setupQueries = db.getValue("setupQueries").jsonArray.map { it.jsonPrimitive.content },
    )
}
