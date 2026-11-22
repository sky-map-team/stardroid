/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

import com.google.android.stardroid.startup.FlagSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * [AnnouncementSource] over a [FlagSource] (iOS). Not frozen at launch like the flags: an
 * activated fetch shows in [current] at once. [refresh] keeps the 12 h throttle; don't bypass it.
 */
class FlagSourceAnnouncementSource(
    private val source: FlagSource,
) : AnnouncementSource {
    override suspend fun refresh() {
        if (!source.canFetch) return
        suspendCancellableCoroutine { continuation ->
            source.fetch(ignoreThrottle = false) { _, _ ->
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    override fun current(): List<Announcement> =
        AnnouncementParser.parse(source.serverText(AnnouncementSource.KEY).orEmpty())
}
