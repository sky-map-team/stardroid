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
package com.google.android.stardroid.education

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.util.AnalyticsInterface
import com.google.android.stardroid.util.MiscUtil
import javax.inject.Inject

/**
 * Handles tap events and coordinates the flow for showing educational info cards.
 *
 * This handler checks if the feature is enabled before attempting to find and display
 * information about celestial objects. In auto mode, info cards are only shown if the
 * user has explicitly enabled the "show_object_info_auto_mode" preference.
 */
class ObjectInfoTapHandler @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val celestialHitTester: CelestialHitTester,
    private val analytics: AnalyticsInterface
) {
    /**
     * Listener interface for when an object is tapped.
     */
    interface ObjectTapListener {
        fun onObjectTapped(objectInfo: ObjectInfo)
    }

    private var objectTapListener: ObjectTapListener? = null

    /**
     * Sets the listener for object tap events.
     */
    fun setObjectTapListener(listener: ObjectTapListener?) {
        this.objectTapListener = listener
    }

    /**
     * Handles a tap event at the given screen coordinates.
     *
     * @param screenX X coordinate of the tap in pixels
     * @param screenY Y coordinate of the tap in pixels
     * @param screenWidth Width of the screen/view in pixels
     * @param screenHeight Height of the screen/view in pixels
     * @return true if a celestial object was tapped and the event was consumed, false otherwise
     */
    fun handleTap(
        screenX: Float,
        screenY: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        // Check if feature is enabled
        if (!isFeatureEnabled()) {
            Log.d(TAG, "Object info feature is disabled")
            return false
        }

        // In auto mode, only show info cards if the user has enabled the option
        val isAutoMode = sharedPreferences.getBoolean(ApplicationConstants.AUTO_MODE_PREF_KEY, true)
        if (isAutoMode) {
            val allowInAutoMode = sharedPreferences.getBoolean(
                ApplicationConstants.SHOW_OBJECT_INFO_AUTO_MODE_PREF_KEY, false)
            if (!allowInAutoMode) {
                Log.d(TAG, "In auto mode, ignoring tap for object info")
                return false
            }
        }

        // Try to find an object at the tap location
        val objectInfo = celestialHitTester.findObjectAtScreenPosition(
            screenX, screenY, screenWidth, screenHeight
        )

        if (objectInfo != null) {
            Log.d(TAG, "Found object: ${objectInfo.id}")
            val b = Bundle()
            b.putString(AnalyticsInterface.OBJECT_INFO_ID, objectInfo.id)
            b.putString(AnalyticsInterface.OBJECT_INFO_TYPE, objectInfo.type.name)
            analytics.trackEvent(AnalyticsInterface.OBJECT_INFO_VIEWED_EVENT, b)
            objectTapListener?.onObjectTapped(objectInfo)
            return true
        }

        Log.d(TAG, "No object found at tap location")
        return false
    }

    /**
     * Returns whether the object info feature is currently enabled.
     */
    fun isFeatureEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            ApplicationConstants.SHOW_OBJECT_INFO_PREF_KEY,
            true
        )
    }

    companion object {
        private val TAG = MiscUtil.getTag(ObjectInfoTapHandler::class.java)
    }
}
