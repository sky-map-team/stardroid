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

/**
 * The ask to post notifications, for a toggle that turns an alert on: it asks only where the
 * platform needs asking and the app does not already hold the permission, and runs [onDenied]
 * if the user refuses, so the toggle can flip back rather than claim an alert that never fires.
 */
@Composable
internal expect fun rememberNotificationPermissionRequest(onDenied: () -> Unit): () -> Unit
