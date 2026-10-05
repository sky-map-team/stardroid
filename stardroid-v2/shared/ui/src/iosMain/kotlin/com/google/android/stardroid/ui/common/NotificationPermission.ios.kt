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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

// iOS asks once; later calls answer at once with the user's earlier choice, so asking on every
// toggle-on is safe. The alerts themselves arrive with the platform surfaces (phase 6).
@Composable
internal actual fun rememberNotificationPermissionRequest(onDenied: () -> Unit): () -> Unit {
    val currentOnDenied by rememberUpdatedState(onDenied)
    return remember {
        {
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
            ) { granted, _ ->
                if (!granted) dispatch_async(dispatch_get_main_queue()) { currentOnDenied() }
            }
        }
    }
}
