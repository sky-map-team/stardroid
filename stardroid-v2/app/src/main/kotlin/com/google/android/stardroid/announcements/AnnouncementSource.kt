/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

/**
 * Where announcements come from. The gms flavor reads Firebase Remote Config; the fdroid flavor
 * has no remote source at all ([None]), so the feature is simply inert there.
 */
interface AnnouncementSource {
    /** Fetch and activate the latest payload. Throttled by the backend; safe to call often. */
    suspend fun refresh()

    /** The currently activated messages, parsed. Cheap and synchronous — reads the cache. */
    fun current(): List<Announcement>

    object None : AnnouncementSource {
        override suspend fun refresh() = Unit

        override fun current(): List<Announcement> = emptyList()
    }
}
