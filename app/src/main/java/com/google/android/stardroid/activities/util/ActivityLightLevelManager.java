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

package com.google.android.stardroid.activities.util;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

/**
 * Controls an activity's illumination levels.
 *
 * @author John Taylor
 *
 */
public class ActivityLightLevelManager implements OnSharedPreferenceChangeListener {
  private ActivityLightLevelChanger lightLevelChanger;
  private SharedPreferences sharedPreferences;
  private enum LightMode {DAY, NIGHT, AUTO}
  public static final String LIGHT_MODE_KEY = "lightmode";
  public ActivityLightLevelManager(ActivityLightLevelChanger lightLevelChanger,
                                   SharedPreferences sharedPreferences) {
    this.lightLevelChanger = lightLevelChanger;
    this.sharedPreferences = sharedPreferences;
  }

  public void onResume() {
    registerWithPreferences();
    LightMode currentMode = getLightModePreference();
    setActivityMode(currentMode);
  }

  private void setActivityMode(LightMode currentMode) {
    switch(currentMode) {
      case DAY:
        lightLevelChanger.setNightMode(false);
        break;
      case NIGHT:
        lightLevelChanger.setNightMode(true);
        break;
      case AUTO:
        throw new UnsupportedOperationException("not implemented yet");
    }
  }

  private LightMode getLightModePreference() {
    String preference = sharedPreferences.getString(LIGHT_MODE_KEY, LightMode.DAY.name());
    return LightMode.valueOf(preference);
  }

  private void registerWithPreferences() {
    sharedPreferences.registerOnSharedPreferenceChangeListener(this);
  }

  public void onPause() {
    unregisterWithPreferences();
  }

  private void unregisterWithPreferences() {
    sharedPreferences.registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (!LIGHT_MODE_KEY.equals(key)) {
      return;
    }
    setActivityMode(getLightModePreference());
  }
}
