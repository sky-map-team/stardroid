/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.startup

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.google.android.stardroid.R
import com.google.android.stardroid.announcements.AnnouncementAction

/**
 * The launch interstitial for a remote announcement. [onOpen] runs the message's action (if it
 * has one) and closes; [onDismiss] hides the message everywhere.
 */
@Composable
fun AnnouncementDialog(
    announcement: PendingAnnouncement,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(announcement.text.title) },
        text = { if (announcement.text.body.isNotBlank()) Text(announcement.text.body) },
        confirmButton = {
            // "Take a look" promises a destination; a plain open-sky message has none.
            val label =
                if (announcement.action is AnnouncementAction.Search) {
                    R.string.announcement_dialog_open
                } else {
                    R.string.announcement_dialog_continue
                }
            TextButton(onClick = onOpen) { Text(stringResource(label)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.announcement_dialog_dismiss))
            }
        },
    )
}
