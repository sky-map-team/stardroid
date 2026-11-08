/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import androidx.compose.runtime.Composable
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.beta_user_help_text
import com.google.android.stardroid.ui.resources.whats_new_ios_announcement
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The What's New body for this platform; iOS has its own because the Android notes describe
 * features it lacks.
 */
internal expect val whatsNewContentResource: StringResource

/** Whether the beta testers' note leads What's New. iOS has no beta track, so it never does. */
internal expect val betaHelpTextShown: Boolean

internal expect val iosAnnouncementShown: Boolean

/** The beta testers' note (Android only, while a beta runs), or nothing, to lead What's New. */
@Composable
internal fun betaHelpHtml(): String =
    if (betaHelpTextShown) stringResource(Res.string.beta_user_help_text) else ""

@Composable
internal fun iosAnnouncementHtml(): String =
    if (iosAnnouncementShown) stringResource(Res.string.whats_new_ios_announcement) else ""
