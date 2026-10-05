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

/** What has happened to one message on this device. */
data class SeenRecord(
    val shownOn: Set<Surface> = emptySet(),
    val dismissed: Boolean = false,
)

/**
 * The dedup rules, as one pure function so every surface asks the same question.
 *
 * - A message fires at most once across the interruptive surfaces (notification, interstitial):
 *   once the user has been told, they are not told again in another place.
 * - The widget is passive and exempt, so it keeps showing the message until it expires. An
 *   explicit in-app dismissal hides it everywhere.
 */
object AnnouncementPolicy {
    /**
     * The message [surface] should show now, or null. Newest-starting first among candidates.
     *
     * @param appVersion the running versionCode, compared against each message's `min_version`
     */
    fun next(
        surface: Surface,
        now: Instant,
        active: List<Announcement>,
        seen: Map<String, SeenRecord>,
        appVersion: Long,
    ): Announcement? =
        active
            .asSequence()
            .filter { surface in it.surfaces && it.isActive(now) && it.minVersion <= appVersion }
            .filter { eligible(surface, seen[it.id] ?: SeenRecord()) }
            .maxByOrNull { it.start }

    private fun eligible(
        surface: Surface,
        record: SeenRecord,
    ): Boolean =
        when {
            record.dismissed -> false
            surface.interruptive -> record.shownOn.none { it.interruptive }
            else -> true
        }
}
