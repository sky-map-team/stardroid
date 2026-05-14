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

import java.util.Map;

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
  // Individual sensor boolean properties (easier to filter in GA4 than parsing DEVICE_SENSORS)
  String HAS_GYRO = "has_gyro_prop";
  String HAS_ROTATION_VECTOR = "has_rotation_vector_prop";
  // Version the user first installed (set once on new install for cohort analysis)
  String FIRST_INSTALL_VERSION = "first_install_version_prop";
  // User's locale at startup (e.g. "en-US") to understand translation coverage
  String USER_LOCALE = "user_locale_prop";

  // Events & Categories
  String TOS_ACCEPTED_EVENT = "TOS_accepted_ev";
  String TOS_REJECTED_EVENT = "TOS_rejected_ev";
  String PREFERENCE_BUTTON_TOGGLE_EVENT = "preference_button_toggled_ev";
  String PREFERENCE_BUTTON_TOGGLE_VALUE = "preference_toggle_value";
  String PREFERENCE_CHANGE_EVENT = "preference_change_ev";
  String PREFERENCE_CHANGE_EVENT_VALUE = "value";
  String MANUAL_MODE_TOGGLED_EVENT = "manual_mode_toggled_ev";
  String MANUAL_MODE_ENABLED = "enabled";
  String HORIZON_LEVEL_TOGGLED_EVENT = "horizon_level_toggled_ev";
  String HORIZON_LEVEL_TOGGLED_VALUE = "enabled";
  String MENU_ITEM_EVENT = "menu_item_pressed_ev";
  String MENU_ITEM_EVENT_VALUE = "menu_item";
  String TOGGLED_NIGHT_MODE_LABEL = "night_mode";
  String SEARCH_REQUESTED_LABEL = "search_requested";
  String SETTINGS_OPENED_LABEL = "settings_opened";
  String CREDITS_OPENED_LABEL = "credits_opened";
  String HELP_OPENED_LABEL = "help_opened";
  String CALIBRATION_OPENED_LABEL = "calibration_opened";
  String TIME_TRAVEL_OPENED_LABEL = "time_travel_opened";
  String GALLERY_OPENED_LABEL = "gallery_opened";
  String TOS_OPENED_LABEL = "TOS_opened";
  String DIAGNOSTICS_OPENED_LABEL = "diagnostics_opened";
  String SEARCH_EVENT = "search";
  String SEARCH_TERM = "search_term";
  String SEARCH_SUCCESS = "search_success"; // parameter name in Firebase, keep as-is
  String SEARCH_FAILED_EVENT = "search_failed_ev";
  String START_EVENT = "start_up_event_ev";
  String START_EVENT_HOUR = "local_hour";
  String START_EVENT_DAY_OF_WEEK = "day_of_week";
  String START_EVENT_NIGHT_MODE = "night_mode_on";
  String START_EVENT_SENSOR_PATH = "sensor_path";
  String SENSOR_PATH_ROTATION_VECTOR = "rotation_vector";
  String SENSOR_PATH_ACCEL_MAG = "accel_mag";
  String SENSOR_PATH_NONE = "none";  // No sensor manager available; app runs in manual mode only

  String TIME_TRAVEL_USED_EVENT = "time_travel_used_ev";
  String TIME_TRAVEL_EVENT_KEY = "travel_event";

  String SESSION_LENGTH_EVENT = "session_length_ev";
  String SESSION_LENGTH_TIME_VALUE = "session_length";
  String SESSION_BUCKET = "session_bucket";

  // Educational card views
  String OBJECT_INFO_VIEWED_EVENT = "object_info_viewed_ev";
  String OBJECT_INFO_ID = "object_id";
  String OBJECT_INFO_TYPE = "object_type";

  // Calibration auto-trigger
  String CALIBRATION_AUTO_TRIGGERED_EVENT = "calibration_auto_triggered_ev";
  String CALIBRATION_TOAST_SHOWN_EVENT = "calibration_toast_shown_ev";

  // Missing sensors
  String NO_SENSORS_WARNING_EVENT = "no_sensors_warning_ev";

  // Gallery image viewed
  String GALLERY_IMAGE_VIEWED_EVENT = "gallery_image_viewed_ev";
  String GALLERY_IMAGE_NAME = "image_name";

  // Object locked (search result activated)
  String OBJECT_LOCKED_EVENT = "object_locked_ev";
  String OBJECT_LOCKED_NAME = "object_name";
  String OBJECT_LOCKED_MODE = "mode";
  String OBJECT_LOCKED_MODE_AUTO = "auto";
  String OBJECT_LOCKED_MODE_MANUAL = "manual";

  // Layer toggles
  String LAYER_TOGGLED_EVENT = "layer_toggled_ev";
  String LAYER_TOGGLED_NAME = "layer_name";
  String LAYER_TOGGLED_ENABLED = "layer_enabled";
  
  // Map loading
  String MAP_LOAD_EVENT = "map_load_ev";
  String MAP_LOAD_SUCCESS = "success";
  String MAP_LOAD_ERROR_CODE = "error_code";
  String MAP_LOAD_PROVIDER = "provider";
  String MAP_LOAD_PROVIDER_STADIA = "stadia";

  // Warm welcome onboarding
  String WARM_WELCOME_STARTED_EVENT = "warm_welcome_started_ev";
  String WARM_WELCOME_STARTED_MANUAL = "is_manual_invocation";
  String WARM_WELCOME_SLIDE_VIEWED_EVENT = "warm_welcome_slide_viewed_ev";
  String WARM_WELCOME_SLIDE_NUMBER = "slide_number";
  String WARM_WELCOME_SKIPPED_EVENT = "warm_welcome_skipped_ev";
  String WARM_WELCOME_COMPLETED_EVENT = "warm_welcome_completed_ev";
  String COMPLETED_WARM_WELCOME = "completed_warm_welcome_prop";
  Map<String, String> LAYER_NAME_MAP = Map.of(
      "source_provider.0", "stars",
      "source_provider.1", "constellations",
      "source_provider.2", "deep_sky_objects",
      "source_provider.3", "solar_system",
      "source_provider.4", "grid",
      "source_provider.5", "horizon",
      "source_provider.6", "meteor_showers"
  );

  static String layerDisplayName(String prefKey) {
    return LAYER_NAME_MAP.getOrDefault(prefKey, prefKey);
  }

  void setEnabled(boolean enabled);

  /**
   * Tracks an event.
   *
   * @see AnalyticsInterface
   */
  void trackEvent(String event, Bundle params);

  void setUserProperty(String propertyName, String propertyValue);
}
