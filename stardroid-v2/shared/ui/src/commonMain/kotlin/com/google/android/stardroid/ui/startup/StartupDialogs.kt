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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.ui.common.StyledHtml
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.beta_user_help_text
import com.google.android.stardroid.ui.resources.dialog_ok_button
import com.google.android.stardroid.ui.resources.whats_new_content
import com.google.android.stardroid.ui.resources.whats_new_dialog_title
import com.google.android.stardroid.ui.resources.whats_new_support
import org.jetbrains.compose.resources.stringResource

/**
 * The app's version name, for the What's New and Help headings (v1 `getVersionName`): the
 * manifest's on Android, the bundle's short version on iOS.
 */
@Composable
expect fun appVersionName(): String

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
    val screenHeight =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.whats_new_dialog_title)) },
        text = {
            // The "New in version X" heading is dropped for the 2.0 launch copy, which opens
            // with its own splash line instead — restore it for later point releases once the
            // launch announcement has aged out.
            val html =
                stringResource(Res.string.whats_new_support) +
                    stringResource(Res.string.beta_user_help_text) +
                    stringResource(Res.string.whats_new_content)
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
                Text(stringResource(Res.string.dialog_ok_button))
            }
        },
    )
}
