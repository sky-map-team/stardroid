/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [AnnouncementSource] over the Remote Config `announcements` parameter (a JSON string; see
 * [AnnouncementParser]). Defaults are seeded by `RemoteConfigExperimentConfig`, which shares
 * the same `FirebaseRemoteConfig` instance.
 *
 * Quota: [refresh] relies on the client's default 12-hour minimum fetch interval, so a worker
 * running twice a day costs the project about two requests per install per day. Do not lower it.
 */
class RemoteConfigAnnouncementSource : AnnouncementSource {
    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    override suspend fun refresh() {
        suspendCancellableCoroutine { continuation ->
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (!task.isSuccessful) Log.w(TAG, "Announcement fetch failed", task.exception)
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    override fun current(): List<Announcement> = AnnouncementParser.parse(remoteConfig.getString(KEY))

    private companion object {
        const val TAG = "RemoteConfigAnnouncements"
        const val KEY = "announcements"
    }
}
