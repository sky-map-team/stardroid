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
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

// Android's single click and double click: a medium impact, and the error notification's
// heavier buzz.
@Composable
internal actual fun rememberSensorCheckBuzz(): (present: Boolean) -> Unit =
    remember {
        { present ->
            if (present) {
                UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
                    .impactOccurred()
            } else {
                UINotificationFeedbackGenerator()
                    .notificationOccurred(
                        UINotificationFeedbackType.UINotificationFeedbackTypeError,
                    )
            }
        }
    }
