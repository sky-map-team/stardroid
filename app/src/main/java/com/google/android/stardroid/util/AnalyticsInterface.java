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

import com.google.android.stardroid.StardroidApplication;
import com.google.firebase.analytics.FirebaseAnalytics;

import javax.inject.Inject;

/**
 * Encapsulates interactions with Firebase Analytics, allowing it to be
 * disabled etc.
 *
 * @author John Taylor
 */
public interface AnalyticsInterface {
  static final String PREF_KEY = "enable_analytics";

  // User properties
  static final String NEW_USER = "new_user";  // Might be the same as the build-in prop - let's check
  static final String DEVICE_SENSORS = "device_sensors"; // Alphabetically ordered list of relevant sensors
  static final String DEVICE_SENSORS_NONE = "none";
  static final String DEVICE_SENSORS_ACCELEROMETER = "accel";
  static final String DEVICE_SENSORS_GYRO = "gyro";
  static final String DEVICE_SENSORS_MAGNETIC = "mag";
  static final String DEVICE_SENSORS_ROTATION = "rot";
  // Phone claims to have a sensor, but then doesn't allow registration of a listener.
  static final String SENSOR_LIAR = "sensor liar";

  // Events & Categories
  static final String TOS_ACCEPT = "Terms Of Service";
  static final String APP_CATEGORY = "Application";
  static final String TOS_ACCEPTED_EVENT = "TOS Accepted";
  static final String TOS_REJECTED_EVENT = "TOS Rejected";
  static final String PREFERENCE_TOGGLE = "Preference toggled";
  static final String PREFERENCE_BUTTON_TOGGLE_EVENT = "Preference button toggled";
  static final String PREFERENCE_BUTTON_TOGGLE_VALUE = "preference_toggle_value";
  static final String PREFERENCE_CHANGE_EVENT = "Preference Change";
  static final String PREFERENCE_CHANGE_EVENT_VALUE = "value";
  static final String USER_ACTION_CATEGORY = "User Action";
  static final String TOGGLED_MANUAL_MODE_LABEL = "Toggled Manual Mode";
  static final String MENU_ITEM = "Pressed Menu Item";
  static final String MENU_ITEM_EVENT_NAME = "menu_item";
  static final String TOGGLED_NIGHT_MODE_LABEL = "Toggled Night Mode";
  static final String SEARCH_REQUESTED_LABEL = "Search Requested";
  static final String SETTINGS_OPENED_LABEL = "Settings Opened";
  static final String HELP_OPENED_LABEL = "Help Opened";
  static final String CALIBRATION_OPENED_LABEL = "Calibration Opened";
  static final String TIME_TRAVEL_OPENED_LABEL = "Time Travel Opened";
  static final String GALLERY_OPENED_LABEL = "Gallery Opened";
  static final String TOS_OPENED_LABEL = "TOS Opened";
  static final String DIAGNOSTICS_OPENED_LABEL = "Diagnostics Opened";
  static final String SEARCH_EVENT = FirebaseAnalytics.Event.SEARCH;
  static final String SEARCH_TERM = FirebaseAnalytics.Param.SEARCH_TERM;
  static final String SEARCH_SUCCESS = "search_success";
  static final String GENERAL_CATEGORY = "General";
  static final String START_EVENT = "Start up event";
  static final String START_EVENT_HOUR = "hour";

  static final String SENSOR_CATEGORY = "Sensors";
  static final String SESSION_LENGTH_EVENT = "Session length";
  static final String SESSION_LENGTH_TIME = "Session length";
  static final String SENSOR_AVAILABILITY = "Minimal Sensor Availability";
  static final String ROT_SENSOR_AVAILABILITY = "Rotation Sensor Availability";
  static final String SENSOR_TYPE = "Sensor Type - ";
  static final String SENSOR_NAME = "Sensor Name";
  static final String HIGH_SENSOR_ACCURACY_ACHIEVED = "High Accuracy Achieved";
  static final String SENSOR_ACCURACY_CHANGED = "Sensor Accuracy Changed";

  void setEnabled(boolean enabled);

  /**
   * Tracks an event.
   *
   * @see com.google.firebase.analytics.FirebaseAnalytics
   */
  void trackEvent(String event, Bundle params);

  void setUserProperty(String propertyName, String propertyValue);
}
