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
     */
    fun isGestureNavigationEnabled(context: Context): Boolean {
        return Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 2
    }
}
