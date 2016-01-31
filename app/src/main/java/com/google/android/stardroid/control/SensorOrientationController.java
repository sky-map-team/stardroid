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

package com.google.android.stardroid.control;

import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.util.smoothers.ExponentiallyWeightedSmoother;
import com.google.android.stardroid.util.smoothers.PlainSmootherModelAdaptor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Sets the direction of view from the orientation sensors.
 *
 * @author John Taylor
 */
public class SensorOrientationController extends AbstractController
    implements OnSharedPreferenceChangeListener {
  // TODO(johntaylor): this class needs to be refactored to use the new
  // sensor API and to behave properly when sensors are not available.

  // Attention - the following strings must match those in strings.xml and notranslate-arrays.xml.
  private static final String SENSOR_SPEED_HIGH = "FAST";
  private static final String SENSOR_SPEED_SLOW = "SLOW";
  private static final String SENSOR_SPEED_STANDARD = "STANDARD";
  private static final String SENSOR_SPEED_PREF_KEY = "sensor_speed";

  private static final String SENSOR_DAMPING_HIGH = "HIGH";
  private static final String SENSOR_DAMPING_STANDARD = "STANDARD";
  private static final String SENSOR_DAMPING_PREF_KEY = "sensor_damping";

  private static class SensorDampingSettings {
    public float damping;
    public int exponent;
    public SensorDampingSettings(float damping, int exponent) {
      this.damping = damping;
      this.exponent = exponent;
    }
  }
  private final static String TAG = MiscUtil.getTag(SensorOrientationController.class);
  /**
   * Parameters that control the smoothing of the accelerometer and
   * magnetic sensors.
   */
  private static final SensorDampingSettings[] ACC_DAMPING_SETTINGS = new SensorDampingSettings[] {
        new SensorDampingSettings(0.7f, 3),
        new SensorDampingSettings(0.7f, 3)
  };
  private static final SensorDampingSettings[] MAG_DAMPING_SETTINGS = new SensorDampingSettings[] {
      new SensorDampingSettings(0.05f, 3),  // Derived for the Nexus One
      new SensorDampingSettings(0.001f, 4)  // Derived for the unpatched MyTouch Slide
  };

  private SensorManager manager;
  private SensorListener accelerometerSmoother;
  private SensorListener compassSmoother;
  private PlainSmootherModelAdaptor modelAdaptor;

  private SharedPreferences sharedPreferences;

  public SensorOrientationController(Context context) {
    manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    sharedPreferences.registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void start() {
    modelAdaptor = new PlainSmootherModelAdaptor(model);

    Log.d(TAG, "Exponentially weighted smoothers used");
    String dampingPreference = sharedPreferences.getString(SENSOR_DAMPING_PREF_KEY,
        SENSOR_DAMPING_STANDARD);
    String speedPreference = sharedPreferences.getString(SENSOR_SPEED_PREF_KEY,
        SENSOR_SPEED_STANDARD);
    Log.d(TAG, "Sensor damping preference " + dampingPreference);
    Log.d(TAG, "Sensor speed preference " + speedPreference);
    int dampingIndex = 0;
    if (SENSOR_DAMPING_HIGH.equals(dampingPreference)) {
      dampingIndex = 1;
    }
    int sensorSpeed = SensorManager.SENSOR_DELAY_GAME;
    if (SENSOR_SPEED_SLOW.equals(speedPreference)) {
      sensorSpeed = SensorManager.SENSOR_DELAY_NORMAL;
    } else if (SENSOR_SPEED_HIGH.equals(speedPreference)) {
      sensorSpeed = SensorManager.SENSOR_DELAY_FASTEST;
    }
    accelerometerSmoother = new ExponentiallyWeightedSmoother(
        modelAdaptor,
        ACC_DAMPING_SETTINGS[dampingIndex].damping,
        ACC_DAMPING_SETTINGS[dampingIndex].exponent);
    compassSmoother = new ExponentiallyWeightedSmoother(
        modelAdaptor,
        MAG_DAMPING_SETTINGS[dampingIndex].damping,
        MAG_DAMPING_SETTINGS[dampingIndex].exponent);

    if (manager != null) {
      manager.registerListener(accelerometerSmoother,
                               SensorManager.SENSOR_ACCELEROMETER,
                               sensorSpeed);
      manager.registerListener(compassSmoother,
                               SensorManager.SENSOR_MAGNETIC_FIELD,
                               sensorSpeed);
    }
    Log.d(TAG, "Registered sensor listener");
  }

  @Override
  public void stop() {
    Log.d(TAG, "Unregistering sensor listeners");
    manager.unregisterListener(accelerometerSmoother);
    manager.unregisterListener(compassSmoother);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (SENSOR_DAMPING_PREF_KEY.equals(key) || SENSOR_SPEED_PREF_KEY.equals(key)) {
      Log.d(TAG, "User sensor preferences changed - restarting sensor controllers");
      stop();
      start();
    }
  }
}
