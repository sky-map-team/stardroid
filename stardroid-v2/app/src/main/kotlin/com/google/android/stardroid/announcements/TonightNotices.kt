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
import com.google.android.stardroid.ui.tonight.TonightNotice
import kotlinx.datetime.Instant

/** The messages the Tonight sheet carries: every active one, in the user's language (D149). */
fun tonightNotices(
    now: Instant,
    source: AnnouncementSource,
    seen: Map<String, SeenRecord>,
    appVersion: Long,
    locale: LocaleSpec,
): List<TonightNotice> =
    AnnouncementPolicy.forSheet(now, source.current(), seen, appVersion).mapNotNull { message ->
        val text = message.localized(locale) ?: return@mapNotNull null
        TonightNotice(
            text.title,
            text.body,
            (message.action as? AnnouncementAction.Search)?.query,
        )
    }
