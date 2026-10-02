/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.settings.AutoDimness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import platform.UIKit.UIScreen

/**
 * Night mode's screen dimming, Android's `MainActivity.applyScreenDimming` (v1
 * `ActivityLightLevelChanger`): the auto-dimness preference sets the level while night mode is
 * on, and day mode leaves the screen as the user set it.
 *
 * Android's override belongs to the window and lapses when the app leaves the screen. iOS's
 * brightness is the whole device's, so this remembers the user's level before dimming and puts
 * it back for day mode and whenever the app goes to the background.
 */
class ScreenDimming(
    nightMode: Flow<Boolean>,
    autoDimness: Flow<AutoDimness>,
    foreground: Flow<Boolean>,
    scope: CoroutineScope,
) {
    /** The user's brightness while ours is applied; null when theirs is showing. */
    private var userBrightness: Double? = null

    init {
        scope.launch {
            combine(nightMode, autoDimness, foreground) { night, dimness, isForeground ->
                if (night && isForeground) levelFor(dimness) else null
            }.collect(::apply)
        }
    }

    private fun apply(level: Double?) {
        val screen = UIScreen.mainScreen
        if (level == null) {
            userBrightness?.let { screen.brightness = it }
            userBrightness = null
        } else {
            if (userBrightness == null) userBrightness = screen.brightness
            screen.brightness = level
        }
    }

    private fun levelFor(dimness: AutoDimness): Double? =
        when (dimness) {
            AutoDimness.SYSTEM -> null
            // Android's BRIGHTNESS_OVERRIDE_OFF: the panel's lowest level.
            AutoDimness.DIM -> 0.0
            // v1's hand-tuned level (20/255), dimmer than most system settings but visible.
            AutoDimness.CLASSIC -> 20.0 / 255.0
        }
}
