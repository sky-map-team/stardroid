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

package com.google.android.stardroid.util;

import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Encapsulates interactions with Firebase Analytics, allowing it to be
 * disabled etc.
 *
 * @author John Taylor
 */
public interface AnalyticsInterface {
  String PREF_KEY = "enable_analytics";

  // User properties
  String NEW_USER = "new_user_prop";  // Might be the same as the build-in prop - let's check
  String DEVICE_SENSORS = "device_sensors_prop"; // Alphabetically ordered list of relevant sensors
  String DEVICE_SENSORS_NONE = "none";
  String DEVICE_SENSORS_ACCELEROMETER = "accel";
  String DEVICE_SENSORS_GYRO = "gyro";
  String DEVICE_SENSORS_MAGNETIC = "mag";
  String DEVICE_SENSORS_ROTATION = "rot";
  // Phone claims to have a sensor, but then doesn't allow registration of a listener.
  String SENSOR_LIAR = "sensor_liar_prop";

  // Events & Categories
  String TOS_ACCEPTED_EVENT = "TOS_accepted_ev";
  String TOS_REJECTED_EVENT = "TOS_rejected_ev";
  String PREFERENCE_BUTTON_TOGGLE_EVENT = "preference_button_toggled_ev";
  String PREFERENCE_BUTTON_TOGGLE_VALUE = "preference_toggle_value";
  String PREFERENCE_CHANGE_EVENT = "preference_change_ev";
  String PREFERENCE_CHANGE_EVENT_VALUE = "value";
  String TOGGLED_MANUAL_MODE_LABEL = "toggled_manual_mode_ev";
  String MENU_ITEM_EVENT = "menu_item_pressed_ev";
  String MENU_ITEM_EVENT_VALUE = "menu_item";
  String TOGGLED_NIGHT_MODE_LABEL = "night_mode";
  String SEARCH_REQUESTED_LABEL = "search_requested";
  String SETTINGS_OPENED_LABEL = "settings_opened";
  String HELP_OPENED_LABEL = "help_opened";
  String CALIBRATION_OPENED_LABEL = "calibration_opened";
  String TIME_TRAVEL_OPENED_LABEL = "time_travel_opened";
  String GALLERY_OPENED_LABEL = "gallery_opened";
  String TOS_OPENED_LABEL = "TOS_opened";
  String DIAGNOSTICS_OPENED_LABEL = "diagnostics_opened";
  String SEARCH_EVENT = FirebaseAnalytics.Event.SEARCH;
  String SEARCH_TERM = FirebaseAnalytics.Param.SEARCH_TERM;
  String SEARCH_SUCCESS = "search_success";
  String START_EVENT = "start_up_event_ev";
  String START_EVENT_HOUR = "hour";

  String SESSION_LENGTH_EVENT = "session_length_ev";
  String SESSION_LENGTH_TIME_VALUE = "session_length";

  void setEnabled(boolean enabled);

  /**
   * Tracks an event.
   *
   * @see com.google.firebase.analytics.FirebaseAnalytics
   */
  void trackEvent(String event, Bundle params);

  void setUserProperty(String propertyName, String propertyValue);
}
