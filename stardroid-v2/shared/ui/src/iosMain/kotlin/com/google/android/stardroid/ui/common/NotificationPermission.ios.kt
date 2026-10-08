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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusDenied
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

// Asked as the screen composes, as Android asks; iOS answers asynchronously.
@Composable
internal actual fun notificationsBlocked(): Boolean {
    val blocked by produceState(false) {
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                val denied = settings?.authorizationStatus == UNAuthorizationStatusDenied
                dispatch_async(dispatch_get_main_queue()) { value = denied }
            }
    }
    return blocked
}

// iOS has no deep link to the notifications page alone; the app's page in Settings holds it.
@Composable
internal actual fun rememberOpenNotificationSettings(): () -> Unit =
    remember {
        {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null)
            }
        }
    }
