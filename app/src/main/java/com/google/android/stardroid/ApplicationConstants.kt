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
package com.google.android.stardroid

import com.google.android.stardroid.math.Vector3

/**
 * A home for the application's few global constants.
 */
object ApplicationConstants {
  const val APP_NAME = "Stardroid"

  /** Default value for 'south' in phone coords when the app starts  */
  @JvmField
  val INITIAL_SOUTH = Vector3(0f, -1f, 0f)

  /** Default value for 'down' in phone coords when the app starts  */
  @JvmField
  val INITIAL_DOWN = Vector3(0f, -1f, -9f)

  // Preference keys
  const val AUTO_MODE_PREF_KEY = "auto_mode"
  const val NO_WARN_ABOUT_MISSING_SENSORS = "no warn about missing sensors"
  const val BUNDLE_TARGET_NAME = "target_name"
  const val BUNDLE_NIGHT_MODE = "night_mode"
  const val BUNDLE_X_TARGET = "bundle_x_target"
  const val BUNDLE_Y_TARGET = "bundle_y_target"
  const val BUNDLE_Z_TARGET = "bundle_z_target"
  const val BUNDLE_SEARCH_MODE = "bundle_search"
  const val SOUND_EFFECTS = "sound_effects"

  // Preference that keeps track of whether or not the user accepted the ToS for this version
  const val READ_TOS_PREF_VERSION = "read_tos_version"
  const val READ_WHATS_NEW_PREF_VERSION = "read_whats_new_version1"
  const val SHARED_PREFERENCE_DISABLE_GYRO = "disable_gyro"

  // Attention - the following strings must match those in strings.xml and notranslate-arrays.xml.
  const val SENSOR_SPEED_HIGH = "FAST"
  const val SENSOR_SPEED_SLOW = "SLOW"
  const val SENSOR_SPEED_STANDARD = "STANDARD"
  const val SENSOR_SPEED_PREF_KEY = "sensor_speed"
  const val SENSOR_DAMPING_REALLY_HIGH = "REALLY HIGH"
  const val SENSOR_DAMPING_EXTRA_HIGH = "EXTRA HIGH"
  const val SENSOR_DAMPING_HIGH = "HIGH"
  const val SENSOR_DAMPING_STANDARD = "STANDARD"
  const val SENSOR_DAMPING_PREF_KEY = "sensor_damping"
  const val REVERSE_MAGNETIC_Z_PREFKEY = "reverse_magnetic_z"
  const val VIEW_MODE_PREFKEY = "viewing_direction" // End Preference Keys
}