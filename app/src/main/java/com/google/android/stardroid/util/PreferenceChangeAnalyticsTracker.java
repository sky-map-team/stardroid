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

package com.google.android.stardroid.util;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks changes in preferences and logs them to Google Analytics.
 *
 * @author John Taylor
 */
public class PreferenceChangeAnalyticsTracker implements OnSharedPreferenceChangeListener {
  private Analytics analytics;
  private static final String TAG = MiscUtil.getTag(PreferenceChangeAnalyticsTracker.class);

  public PreferenceChangeAnalyticsTracker(Analytics analytics) {
    this.analytics = analytics;
  }

  private Set<String> stringPreferenceWhiteList = new HashSet<String>(Arrays.asList(new String[] {
      "sensor_speed", "sensor_damping"
  }));

  private void trackPreferenceChange(SharedPreferences sharedPreferences, String key) {
    Log.d(TAG, "Logging pref change " + key);
    // There is no way to get a preference without knowing its type.  Consequently, we try
    // each type and silently swallow the exception if we guess wrong.  If this proves expensive
    // we might switch to caching the type.
    try {
      String value = sharedPreferences.getString(key, "unknown");
      // Unlike the numeric valued preferences, we could inadvertently log PII if we blindly log
      // all String values.
      // Instead we maintain a whitelist of things we're allowed to log.  If the key isn't in
      // the whitelist, then we only log that it changed, not what it changed to.
      if (!stringPreferenceWhiteList.contains(key)) {
        value = "PII";
      }
      analytics.trackEvent(Analytics.USER_ACTION_CATEGORY,
                           "Preference: " + key, "Preference: " + value, 0);
      return;
    } catch (ClassCastException cce) {
      // Thrown if the pref wasn't a string.
    }
    try {
      boolean value = sharedPreferences.getBoolean(key, false);
      analytics.trackEvent(Analytics.USER_ACTION_CATEGORY,
                           Analytics.PREFERENCE_TOGGLE, "Preference:" + key,  value ? 1 : 0);
      return;
    } catch (ClassCastException cce) {
      // Thrown if the pref wasn't a boolean.
    }
    try {
      int value = sharedPreferences.getInt(key, 0);
      analytics.trackEvent(Analytics.USER_ACTION_CATEGORY,
                           Analytics.PREFERENCE_TOGGLE, "Preference:" + key, value);
      return;
    } catch (ClassCastException cce) {
      // Thrown if the pref wasn't an integer.
    }
    try {
      @SuppressWarnings("unused")
      long unused = sharedPreferences.getLong(key, 0);
      analytics.trackEvent(Analytics.USER_ACTION_CATEGORY,
                           Analytics.PREFERENCE_TOGGLE, "Preference:" + key, 0);
      return;
    } catch (ClassCastException cce) {
      // Thrown if the pref wasn't an integer.
    }
    try {
      @SuppressWarnings("unused")
      float unused = sharedPreferences.getFloat(key, 0);
      analytics.trackEvent(Analytics.USER_ACTION_CATEGORY,
                           Analytics.PREFERENCE_TOGGLE, "Preference:" + key, 0);
      return;
    } catch (ClassCastException cce) {
      // Thrown if the pref wasn't a float.
    }
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    trackPreferenceChange(sharedPreferences, key);
  }
}
