/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.notifications

/**
 * Every notification the app posts. [id] is both the `notify` id and the content PendingIntent's
 * request code: those intents differ only in extras, which PendingIntent matching ignores, so a
 * shared code would let FLAG_UPDATE_CURRENT overwrite another notification's tap target.
 * The values are arbitrary (they once started at 71 for no reason; 42 is no better), so only
 * uniqueness matters and renumbering is cheap.
 */
enum class SkyNotification(
    val id: Int,
) {
    /** Tonight's digest or shower alert: a repost replaces, never stacks. */
    TONIGHT(42),
    SATELLITE_PASS(43),
    LUNAR_ECLIPSE(44),
    ANNOUNCEMENT(45),
}
