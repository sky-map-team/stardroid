// Copyright 2010 Google Inc.
//
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
package com.google.android.stardroid.util

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Encapsulates interactions with Firebase Analytics, allowing it to be
 * disabled etc.
 *
 * @author John Taylor
 */
interface AnalyticsInterface {
  fun setEnabled(enabled: Boolean)

  /**
   * Tracks an event.
   *
   * @see com.google.firebase.analytics.FirebaseAnalytics
   */
  fun trackEvent(event: String?, params: Bundle?)
  fun setUserProperty(propertyName: String?, propertyValue: String?)

  companion object {
    const val PREF_KEY = "enable_analytics"

    // User properties
    const val NEW_USER = "new_user_prop" // Might be the same as the build-in prop - let's check
    const val DEVICE_SENSORS =
      "device_sensors_prop" // Alphabetically ordered list of relevant sensors
    const val DEVICE_SENSORS_NONE = "none"
    const val DEVICE_SENSORS_ACCELEROMETER = "accel"
    const val DEVICE_SENSORS_GYRO = "gyro"
    const val DEVICE_SENSORS_MAGNETIC = "mag"
    const val DEVICE_SENSORS_ROTATION = "rot"

    // Phone claims to have a sensor, but then doesn't allow registration of a listener.
    const val SENSOR_LIAR = "sensor_liar_prop"

    // Events & Categories
    const val TOS_ACCEPTED_EVENT = "TOS_accepted_ev"
    const val TOS_REJECTED_EVENT = "TOS_rejected_ev"
    const val PREFERENCE_BUTTON_TOGGLE_EVENT = "preference_button_toggled_ev"
    const val PREFERENCE_BUTTON_TOGGLE_VALUE = "preference_toggle_value"
    const val PREFERENCE_CHANGE_EVENT = "preference_change_ev"
    const val PREFERENCE_CHANGE_EVENT_VALUE = "value"
    const val TOGGLED_MANUAL_MODE_LABEL = "toggled_manual_mode_ev"
    const val MENU_ITEM_EVENT = "menu_item_pressed_ev"
    const val MENU_ITEM_EVENT_VALUE = "menu_item"
    const val TOGGLED_NIGHT_MODE_LABEL = "night_mode"
    const val SEARCH_REQUESTED_LABEL = "search_requested"
    const val SETTINGS_OPENED_LABEL = "settings_opened"
    const val HELP_OPENED_LABEL = "help_opened"
    const val CALIBRATION_OPENED_LABEL = "calibration_opened"
    const val TIME_TRAVEL_OPENED_LABEL = "time_travel_opened"
    const val GALLERY_OPENED_LABEL = "gallery_opened"
    const val TOS_OPENED_LABEL = "TOS_opened"
    const val DIAGNOSTICS_OPENED_LABEL = "diagnostics_opened"
    const val SEARCH_EVENT = FirebaseAnalytics.Event.SEARCH
    const val SEARCH_TERM = FirebaseAnalytics.Param.SEARCH_TERM
    const val SEARCH_SUCCESS = "search_success"
    const val START_EVENT = "start_up_event_ev"
    const val START_EVENT_HOUR = "hour"
    const val SESSION_LENGTH_EVENT = "session_length_ev"
    const val SESSION_LENGTH_TIME_VALUE = "session_length"
  }
}