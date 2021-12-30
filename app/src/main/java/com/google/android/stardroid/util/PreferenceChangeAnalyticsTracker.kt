// Copyright 2011 Google Inc.
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

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.util.Log
import com.google.android.stardroid.util.MiscUtil.getTag
import javax.inject.Inject

/**
 * Tracks changes in preferences and logs them to Google Analytics.
 *
 * @author John Taylor
 */
class PreferenceChangeAnalyticsTracker @Inject internal constructor(private val analytics: Analytics) :
  OnSharedPreferenceChangeListener {
  private val stringPreferenceWhiteList: Set<String> =
    setOf(
      "sensor_speed", "sensor_damping, lightmode"
    )

  private fun trackPreferenceChange(sharedPreferences: SharedPreferences, key: String) {
    Log.d(TAG, "Logging pref change $key")
    // There is no way to get a preference without knowing its type.  Consequently, we try
    // each type and silently swallow the exception if we guess wrong.  If this proves expensive
    // we might switch to caching the type.
    val prefBundle = Bundle()
    val value = getPreferenceAsString(sharedPreferences, key)
    prefBundle.putString(AnalyticsInterface.PREFERENCE_CHANGE_EVENT_VALUE, "$key:$value")
    analytics.trackEvent(AnalyticsInterface.PREFERENCE_CHANGE_EVENT, prefBundle)
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
    trackPreferenceChange(sharedPreferences, key)
  }

  private fun getPreferenceAsString(sharedPreferences: SharedPreferences, key: String): String? {
    var value: String? = "unknown"
    try {
      value = sharedPreferences.getString(key, "unknown")
      if (!stringPreferenceWhiteList.contains(key)) {
        value = "PII"
      }
    } catch (cce: ClassCastException) {
      // Thrown if the pref wasn't a string.
    }
    try {
      value = sharedPreferences.getBoolean(key, false).toString()
    } catch (cce: ClassCastException) {
      // Thrown if the pref wasn't a boolean.
    }
    try {
      value = sharedPreferences.getInt(key, 0).toString()
    } catch (cce: ClassCastException) {
      // Thrown if the pref wasn't an integer.
    }
    try {
      value = sharedPreferences.getLong(key, 0).toString()
    } catch (cce: ClassCastException) {
      // Thrown if the pref wasn't an integer.
    }
    try {
      value = sharedPreferences.getFloat(key, 0f).toString()
    } catch (cce: ClassCastException) {
      // Thrown if the pref wasn't a float.
    }
    // Other types are possible, but those are the ones we care about.
    return value
  }

  companion object {
    private val TAG = getTag(PreferenceChangeAnalyticsTracker::class.java)
  }
}