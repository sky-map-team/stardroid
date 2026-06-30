/*
 * Copyright (c) 2026 Penterakt LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.activities.util

import android.content.Context
import android.provider.Settings

/** Utility for detecting whether the device is using gesture or classic on-screen navigation. */
object NavigationModeUtil {
    /**
     * Returns true if gesture navigation is enabled, false if the user is on the classic
     * 2-button or 3-button on-screen navigation bar.
     *
     * Reads the same "navigation_mode" setting Android itself uses internally
     * (0 = 3-button, 1 = 2-button [deprecated], 2 = gesture). The key isn't a publicly
     * documented constant, but it has been stable since Android 10 (Q).
     *
     * Falls back to false (classic navigation) if the read fails, e.g. due to a
     * SecurityException on a restricted profile or customized ROM. That's a safe default:
     * it just means an extra close button is shown, never a crash.
     */
    fun isGestureNavigationEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 2
        } catch (e: Exception) {
            false
        }
    }
}
