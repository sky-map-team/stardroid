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
  public interface NightModeable {
    void setNightMode(boolean nightMode);
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

  // current setting.
  public void setNightMode(boolean nightMode) {
    if (nightModeable != null) {
      nightModeable.setNightMode(nightMode);
    }
    Window window = activity.getWindow();
    WindowManager.LayoutParams params = window.getAttributes();
    if (nightMode) {
      params.screenBrightness = BRIGHTNESS_DIM;
      params.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;    // TODO(jontayler): look at this again - at present night mode can be brighter than the phone's
    } else {
      params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
      params.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
    }
    window.setAttributes(params);
  }
}