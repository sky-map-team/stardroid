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

import com.google.android.stardroid.base.Nullable;
import com.google.android.stardroid.util.OsVersions;

import android.app.Activity;
import android.view.Window;
import android.view.WindowManager;

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
  public static interface NightModeable {
    public void setNightMode(boolean nightMode);
  }

  // This value is based on inspecting the Android source code for the
  // SettingsAppWidgetProvider:
  // http://hi-android.info/src/com/android/settings/widget/SettingsAppWidgetProvider.java.html
  // (We know that 0.05 is OK on the G1 and N1, but not some other phones, so we don't make this
  // as dim as we could...)
  private static final float BRIGHTNESS_DIM = (float) 20f / 255f;

  private NightModeable nightModeable;
  private Activity activity;

  /**
   * Wraps an activity with a setNightMode method.
   *
   * @param activity the activity under control
   * @param nightmodeable Allows an activity to have a custom night mode method.  May be null.
   */
  public ActivityLightLevelChanger(Activity activity, @Nullable NightModeable nightmodeable) {
    this.activity = activity;
    this.nightModeable = nightmodeable;
  }

  public void setNightMode(boolean nightMode) {
    if (nightModeable != null) {
      nightModeable.setNightMode(nightMode);
    }
    float screenBrightness;
    float buttonBrightness;
    if (nightMode) {
      screenBrightness = BRIGHTNESS_DIM;
      buttonBrightness = OsVersions.brightnessOverrideOffValue();
    } else {
      screenBrightness = OsVersions.brightnessOverrideNoneValue();
      buttonBrightness = OsVersions.brightnessOverrideNoneValue();
    }
    Window window = activity.getWindow();
    WindowManager.LayoutParams params = window.getAttributes();
    OsVersions.setButtonBrightness(buttonBrightness, params);
    params.screenBrightness = screenBrightness;
    window.setAttributes(params);
  }
}
