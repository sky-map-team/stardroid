/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.onboarding

import androidx.compose.runtime.Composable

/**
 * v1 `WarmWelcomeActivity.buzz()`, for the welcome's sensor check: as it reveals each sensor,
 * a short confident tap when the sensor is present and a heavier buzz when it is missing.
 * Android's vibrator; iOS's feedback generators. Does nothing on hardware without haptics.
 */
@Composable
internal expect fun rememberSensorCheckBuzz(): (present: Boolean) -> Unit
