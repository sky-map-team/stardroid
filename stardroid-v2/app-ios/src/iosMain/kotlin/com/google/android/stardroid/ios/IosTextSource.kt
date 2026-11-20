/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.ios

import com.google.android.stardroid.text.TextId
import com.google.android.stardroid.text.TextSource
import com.google.android.stardroid.text.TextTemplate
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.allStringResources
import com.google.android.stardroid.ui.resources.hud_cardinal_directions
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray

/**
 * The shared [TextId]s in the app's current language, by [TextId.resourceName]. Compose Resources
 * are read with `suspend` calls and [TextSource.text] is not, so every string is read here, once
 * per planning run, and the source answers from that.
 */
internal suspend fun loadIosTextSource(): TextSource {
    val compass = getStringArray(Res.array.hud_cardinal_directions)
    val templates =
        TextId.entries
            .filter { it !in TextId.compassPoints }
            .associateWith { id ->
                getString(
                    checkNotNull(Res.allStringResources[id.resourceName]) {
                        "No string resource ${id.resourceName} for $id"
                    },
                )
            }
    return TextSource { id, args ->
        val point = TextId.compassPoints.indexOf(id)
        if (point >= 0) compass[point] else TextTemplate.fill(templates.getValue(id), args)
    }
}
