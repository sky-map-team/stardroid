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

import android.app.Activity;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * Controls the brightness level of an activity.
 *
 * @author John Taylor
 */
public class ActivityLightLevelChanger {
  /**
   * Activities that have some kind of custom night mode (rather than just
   * dimming the screen) implement this.
   *
   * @author John Taylor
   *
   */
  public interface NightModeable {
    void setNightMode(boolean nightMode);
  }

  // Back in the bad old days it was hard to get the brightness level right.  On some phones
  // a particular level would be invisible, on others too bright.  We settled on the following
  // value after some experimentation....
  // This value is based on inspecting the Android source code for the
  // SettingsAppWidgetProvider:
  // http://hi-android.info/src/com/android/settings/widget/SettingsAppWidgetProvider.java.html
  // (We know that 0.05 is OK on the G1 and N1, but not some other phones, so we don't make this
  // as dim as we could...)
  private static final float BRIGHTNESS_DIM_ORIGINAL = 20f / 255f;

  // Following must match the values defined in notranslate-arrays.xml
  private enum DIM_OPTIONS {DIM, SYSTEM, CLASSIC};

  private final NightModeable nightModeable;
  private final Window window;
  private final SharedPreferences sharedPreferences;

  /**
   * Wraps an activity with a setNightMode method.
   *
   * @param window the activity under control
   * @param nightmodeable Allows an activity to have a custom night mode method.  May be null.
   */
  @Inject
  public ActivityLightLevelChanger(Window window, SharedPreferences sharedPreferences,
                                   @Nullable NightModeable nightmodeable) {
    this.window = window;
    this.sharedPreferences = sharedPreferences;
    this.nightModeable = nightmodeable;
  }

  public void setNightMode(boolean nightMode) {

    if (nightModeable != null) {
      nightModeable.setNightMode(nightMode);
    }
    WindowManager.LayoutParams params = window.getAttributes();

    if (nightMode) {
      DIM_OPTIONS dimnessOption = DIM_OPTIONS.valueOf(sharedPreferences.getString(
          ApplicationConstants.AUTO_DIMNESS, DIM_OPTIONS.SYSTEM.toString()));
      float dimnessSetting = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
      switch(dimnessOption) {
        case DIM:
          dimnessSetting = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
          break;
        case CLASSIC:
          dimnessSetting = BRIGHTNESS_DIM_ORIGINAL;
          break;
        default:
          // System setting.
      }
      params.screenBrightness = dimnessSetting;
      params.buttonBrightness = dimnessSetting;
    } else {
      params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
      params.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
    }
    window.setAttributes(params);


  }
}