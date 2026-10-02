/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

import com.google.android.stardroid.catalog.LocaleSpec
import kotlinx.datetime.Instant

/** Where an [Announcement] may appear. The maintainer picks a subset per message. */
enum class Surface(val wire: String) {
    NOTIFICATION("notification"),
    WIDGET("widget"),
    INTERSTITIAL("interstitial"),
    ;

    /** Interruptive surfaces fire at most once per message, across all of them. */
    val interruptive: Boolean get() = this != WIDGET

    companion object {
        fun fromWire(wire: String): Surface? = entries.firstOrNull { it.wire == wire }
    }
}

/** What tapping the message does. */
sealed interface AnnouncementAction {
    /** Just open the map. */
    data object OpenSky : AnnouncementAction

    /** Open the map on the named object's info card. */
    data class Search(val query: String) : AnnouncementAction
}

data class AnnouncementText(val title: String, val body: String)

/**
 * One remotely authored message (docs/design/remote-announcements.md). [id] is the dedup key:
 * reusing an id re-shows nothing, so a corrected message needs a new one.
 */
data class Announcement(
    val id: String,
    val start: Instant,
    val end: Instant,
    val surfaces: Set<Surface>,
    val minVersion: Long,
    val text: Map<String, AnnouncementText>,
    val action: AnnouncementAction,
) {
    fun isActive(now: Instant): Boolean = now >= start && now < end

    /**
     * The text for [locale], walking its fallback chain (`pt-br` → `pt` → `en`). Payload keys
     * are matched case-insensitively. Null only if the payload carries no usable language,
     * which the parser rejects, so callers can treat null as "drop this message".
     */
    fun localized(locale: LocaleSpec): AnnouncementText? =
        locale.fallbackChain.firstNotNullOfOrNull { text[it] }
}
