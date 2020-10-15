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
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

/**
 * Tracks changes in preferences and logs them to Google Analytics.
 *
 * @author John Taylor
 */
public class PreferenceChangeAnalyticsTracker implements OnSharedPreferenceChangeListener {
  private Analytics analytics;
  private static final String TAG = MiscUtil.getTag(PreferenceChangeAnalyticsTracker.class);

  @Inject
  PreferenceChangeAnalyticsTracker(Analytics analytics) {
    this.analytics = analytics;
  }

  private Set<String> stringPreferenceWhiteList = new HashSet<String>(Arrays.asList(new String[] {
      "sensor_speed", "sensor_damping, lightmode"
  }));

  private void trackPreferenceChange(SharedPreferences sharedPreferences, String key) {
    Log.d(TAG, "Logging pref change " + key);
    // There is no way to get a preference without knowing its type.  Consequently, we try
    // each type and silently swallow the exception if we guess wrong.  If this proves expensive
    // we might switch to caching the type.
    Bundle prefBundle = new Bundle();
    String value = getPreferenceAsString(sharedPreferences, key);
    prefBundle.putString(Analytics.PREFERENCE_CHANGE_EVENT_VALUE, key + ":" + value);
    analytics.trackEvent(Analytics.PREFERENCE_CHANGE_EVENT, prefBundle);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    trackPreferenceChange(sharedPreferences, key);
  }

  private String getPreferenceAsString(SharedPreferences sharedPreferences, String key) {
    String value = "unknown";
    try {
      value = sharedPreferences.getString(key, "unknown");
      if (!stringPreferenceWhiteList.contains(key)) {
        value = "PII";
      }
    } catch (ClassCastException cce) {
      // Thrown if the pref wasn't a string.
    }
    try {
      value = Boolean.toString(sharedPreferences.getBoolean(key, false));
    } catch (ClassCastException cce) {
      // Thrown if the pref wasn't a boolean.
    }
    try {
      value = Integer.toString(sharedPreferences.getInt(key, 0));
    } catch (ClassCastException cce) {
      // Thrown if the pref wasn't an integer.
    }
    try {
      value = Long.toString(sharedPreferences.getLong(key, 0));
    } catch (ClassCastException cce) {
      // Thrown if the pref wasn't an integer.
    }
    try {
      value = Float.toString(sharedPreferences.getFloat(key, 0));
    } catch (ClassCastException cce) {
      // Thrown if the pref wasn't a float.
    }
    // Other types are possible, but those are the ones we care about.
    return value;
  }
}
