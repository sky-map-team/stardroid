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

import com.google.android.stardroid.StardroidApplication;

import javax.inject.Inject;

/**
 * Encapsulates interactions with Google Analytics, allowing it to be
 * disabled etc.
 *
 * @author John Taylor
 */
public interface AnalyticsInterface {
  static final String PREF_KEY = "enable_analytics";

  /**
   * Custom vars (for slicing and dicing)
   * At most 5 can be defined.
   */
  static enum Slice {
    ANDROID_OS, SKYMAP_VERSION, DEVICE_NAME, NEW_USER;
  }

  // Page Views
  static final String APPLICATION_CREATE = "/ApplicationCreate";
  static final String COMPASS_CALIBRATION_ACTIVITY = "/MainPage/Calibration";
  static final String DIAGNOSTICS_ACTIVITY = "/MainPage/Diagnostics";
  static final String DYNAMIC_STARMAP_ACTIVITY = "/MainPage";
  static final String EDIT_SETTINGS_ACTIVITY = "/MainPage/EditSettings";
  static final String SPLASH_SCREEN_ACTIVITY = "/SplashScreen";
  static final String IMAGE_GALLERY_ACTIVITY = "/MainPage/ImageGallery";
  static final String IMAGE_DISPLAY_ACTIVITY = "/MainPage/ImageGallery/ImageDisplay";

  // Events & Categories
  static final String TOS_ACCEPT = "Terms Of Service";
  static final String APP_CATEGORY = "Application";
  static final String TOS_ACCEPTED = "TOS Accepted";
  static final String TOS_REJECTED = "TOS Rejected";
  static final String INSTALL_CATEGORY = "Installation";
  static final String INSTALL_EVENT = "Installed Version: ";
  static final String PREVIOUS_VERSION = "Prevous Version: ";
  static final String PREFERENCE_TOGGLE = "Preference toggled";
  static final String PREFERENCE_BUTTON_TOGGLE = "Preference button toggled";
  static final String USER_ACTION_CATEGORY = "User Action";
  static final String TOGGLED_MANUAL_MODE_LABEL = "Toggled Manual Mode";
  static final String MENU_ITEM = "Pressed Menu Item";
  static final String TOGGLED_NIGHT_MODE_LABEL = "Toggled Night Mode";
  static final String SEARCH_REQUESTED_LABEL = "Search Requested";
  static final String SETTINGS_OPENED_LABEL = "Settings Opened";
  static final String HELP_OPENED_LABEL = "Help Opened";
  static final String CALIBRATION_OPENED_LABEL = "Calibration Opened";
  static final String TIME_TRAVEL_OPENED_LABEL = "Time Travel Opened";
  static final String GALLERY_OPENED_LABEL = "Gallery Opened";
  static final String TOS_OPENED_LABEL = "TOS Opened";
  static final String DIAGNOSTICS_OPENED_LABEL = "Diagnostics Opened";
  static final String SEARCH = "Search";
  static final String GENERAL_CATEGORY = "General";
  static final String START_HOUR = "Start up hour";

  static final String SENSOR_CATEGORY = "Sensors";
  static final String SESSION_LENGTH_BUCKET = "Session length bucket";
  static final String SENSOR_AVAILABILITY = "Minimal Sensor Availability";
  static final String ROT_SENSOR_AVAILABILITY = "Rotation Sensor Availability";
  static final String SENSOR_TYPE = "Sensor Type - ";
  static final String SENSOR_NAME = "Sensor Name";
  static final String HIGH_SENSOR_ACCURACY_ACHIEVED = "High Accuracy Achieved";
  static final String SENSOR_ACCURACY_CHANGED = "Sensor Accuracy Changed";
  // Phone claims to have a sensor, but then doesn't allow registration of a listener.
  static final String SENSOR_LIAR = "Sensor Liar!";

  void setEnabled(boolean enabled);

  /**
   * Tracks a screen view.
   */
  void trackPageView(String page);

  /**
   * Tracks and event.
   *
   * @see com.google.android.gms.analytics.HitBuilders.EventBuilder
   */
  void trackEvent(String category, String action, String label, long value);

  /**
   * Sets custom variables for slicing.
   */
  void setCustomVar(Slice slice, String value);
}
