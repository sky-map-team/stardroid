/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.startup

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import com.google.android.stardroid.ui.common.StyledHtml

/** The manifest version name, for the What's New and Help headings (v1 `getVersionName`). */
@Composable
fun appVersionName(): String {
    val context = LocalContext.current
    return remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}

/**
 * v1 `WhatsNewDialogFragment`: the support ask and beta-feedback note lead, ahead of the
 * per-release feature list, so a long feature list can't push the support ask below the fold.
 * Shown on upgrades (never fresh installs — the warm welcome marks it seen). Any dismissal marks
 * the current version seen, as v1's single OK/close path did.
 */
@Composable
fun WhatsNewDialog(
    nightMode: Boolean,
    onDismiss: () -> Unit,
) {
    // The default AlertDialog wraps its content width, so a release with a lot of text ends up
    // tall and narrow. Pin a fixed width and cap the height to a fraction of the screen so the
    // dialog keeps a pleasing aspect ratio and scrolls internally instead of stretching.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_dialog_title)) },
        text = {
            // The "New in version X" heading is dropped for the 2.0 launch copy, which opens
            // with its own splash line instead — restore it for later point releases once the
            // launch announcement has aged out.
            val html =
                stringResource(R.string.whats_new_support) +
                    stringResource(R.string.beta_user_help_text) +
                    stringResource(R.string.whats_new_content)
            StyledHtml(
                html,
                nightMode = nightMode,
                modifier =
                    Modifier
                        .width(320.dp)
                        .heightIn(max = screenHeight * 0.6f)
                        .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_ok_button))
            }
        },
    )
}
