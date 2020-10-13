// Copyright 2008 Google Inc.
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

package com.google.android.stardroid;

import com.google.android.stardroid.units.Vector3;

/**
 * A home for the application's few global constants.
 */
public class ApplicationConstants {

  public static final String APP_NAME = "Stardroid";
  /** Default value for 'south' in phone coords when the app starts */
  public static final Vector3 INITIAL_SOUTH = new Vector3(0, -1, 0);
  /** Default value for 'down' in phone coords when the app starts */
  public static final Vector3 INITIAL_DOWN = new Vector3(0, -1, -9);

  // Preference keys
  public static final String AUTO_MODE_PREF_KEY = "auto_mode";
  public static final String NO_WARN_ABOUT_MISSING_SENSORS = "no warn about missing sensors";
  public static final String BUNDLE_TARGET_NAME = "target_name";
  public static final String BUNDLE_NIGHT_MODE = "night_mode";
  public static final String BUNDLE_X_TARGET = "bundle_x_target";
  public static final String BUNDLE_Y_TARGET = "bundle_y_target";
  public static final String BUNDLE_Z_TARGET = "bundle_z_target";
  public static final String BUNDLE_SEARCH_MODE = "bundle_search";
  public static final String SOUND_EFFECTS = "sound_effects";
  // Preference that keeps track of whether or not the user accepted the ToS for this version
  public static final String READ_TOS_PREF_VERSION = "read_tos_version";
  public static final String READ_WHATS_NEW_PREF_VERSION = "read_whats_new_version1";
  public static final String SHARED_PREFERENCE_DISABLE_GYRO = "disable_gyro";
  // Attention - the following strings must match those in strings.xml and notranslate-arrays.xml.
  public static final String SENSOR_SPEED_HIGH = "FAST";
  public static final String SENSOR_SPEED_SLOW = "SLOW";
  public static final String SENSOR_SPEED_STANDARD = "STANDARD";
  public static final String SENSOR_SPEED_PREF_KEY = "sensor_speed";
  public static final String SENSOR_DAMPING_REALLY_HIGH = "REALLY HIGH";
  public static final String SENSOR_DAMPING_EXTRA_HIGH = "EXTRA HIGH";
  public static final String SENSOR_DAMPING_HIGH = "HIGH";
  public static final String SENSOR_DAMPING_STANDARD = "STANDARD";
  public static final String SENSOR_DAMPING_PREF_KEY = "sensor_damping";
  public static final String REVERSE_MAGNETIC_Z_PREFKEY = "reverse_magnetic_z";
  public static final String ROTATE_HORIZON_PREFKEY = "rotate_horizon";


  // End Preference Keys

}
