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
import com.google.android.stardroid.util.Experiment
import com.google.android.stardroid.util.ExperimentConfig
import com.google.android.stardroid.util.MiscUtil
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import kotlin.math.abs

/**
 * Handles tap events and coordinates the flow for showing educational info cards.
 *
 * This handler checks if the feature is enabled before attempting to find and display
 * information about celestial objects. In auto mode, info cards are only shown if the
 * user has explicitly enabled the "show_object_info_auto_mode" preference.
 */
@ActivityScoped
class ObjectInfoTapHandler @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val celestialHitTester: CelestialHitTester,
    private val analytics: AnalyticsInterface,
    private val experimentConfig: ExperimentConfig
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

        // Only register taps that fall within a centered region of the screen. This avoids
        // accidentally opening info cards when reaching for edge UI (menu, toolbar, etc.).
        // The fraction is experiment-tunable; a value >= 1 disables the restriction entirely.
        val regionFraction = experimentConfig
            .getDouble(Experiment.INFO_CARD_TAP_REGION_FRACTION, DEFAULT_TAP_REGION_FRACTION)
            .toFloat()
        // A fraction <= 0 (misconfigured) or >= 1 disables the restriction entirely so we never
        // accidentally swallow every tap and break the feature.
        if (regionFraction > 0f && regionFraction < 1f) {
            val halfWidth = screenWidth * regionFraction / 2f
            val halfHeight = screenHeight * regionFraction / 2f
            if (abs(screenX - screenWidth / 2f) > halfWidth ||
                abs(screenY - screenHeight / 2f) > halfHeight) {
                Log.d(TAG, "Tap outside central info-card region; ignoring")
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

        /**
         * Default fraction of the screen (centered, both axes) in which taps may open an info
         * card. Used when no remote config value is available.
         * See [Experiment.INFO_CARD_TAP_REGION_FRACTION].
         */
        const val DEFAULT_TAP_REGION_FRACTION = 0.6
    }
}
