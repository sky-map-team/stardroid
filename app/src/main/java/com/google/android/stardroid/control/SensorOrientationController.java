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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.util.SensorAccuracyReporter;
import com.google.android.stardroid.util.smoothers.ExponentiallyWeightedSmoother;
import com.google.android.stardroid.util.smoothers.PlainSmootherModelAdaptor;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Sets the direction of view from the orientation sensors.
 *
 * @author John Taylor
 */
public class SensorOrientationController extends AbstractController
    implements OnSharedPreferenceChangeListener, SensorEventListener {
  // TODO(johntaylor): this class needs to be refactored to use the new
  // sensor API and to behave properly when sensors are not available.

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
        new SensorDampingSettings(0.7f, 3),
        new SensorDampingSettings(0.1f, 3),
        new SensorDampingSettings(0.1f, 3),
  };
  private static final SensorDampingSettings[] MAG_DAMPING_SETTINGS = new SensorDampingSettings[] {
      new SensorDampingSettings(0.05f, 3),  // Derived for the Nexus One
      new SensorDampingSettings(0.001f, 4),  // Derived for the unpatched MyTouch Slide
      new SensorDampingSettings(0.0001f, 5),  // Just guessed for Nexus 6
      new SensorDampingSettings(0.000001f, 5)  // Just guessed for Nexus 6
  };

  private SensorManager manager;
  private SensorListener accelerometerSmoother;
  private SensorListener compassSmoother;
  private Provider<PlainSmootherModelAdaptor> modelAdaptorProvider;
  private SensorAccuracyReporter accuracyReporter;
  private Sensor rotationSensor;

  private SharedPreferences sharedPreferences;

  @Inject
  SensorOrientationController(Context context, SensorAccuracyReporter accuracyReporter,
                              Provider<PlainSmootherModelAdaptor> modelAdaptorProvider,
                              SensorManager manager) {
    this.manager = manager;
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    this.accuracyReporter = accuracyReporter;
    this.modelAdaptorProvider = modelAdaptorProvider;
    this.rotationSensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
  }

  @Override
  public void start() {
    PlainSmootherModelAdaptor modelAdaptor = modelAdaptorProvider.get();

    Log.d(TAG, "Exponentially weighted smoothers used");
    String dampingPreference = sharedPreferences.getString(
        ApplicationConstants.SENSOR_DAMPING_PREF_KEY,
        ApplicationConstants.SENSOR_DAMPING_STANDARD);
    String speedPreference = sharedPreferences.getString(ApplicationConstants.SENSOR_SPEED_PREF_KEY,
        ApplicationConstants.SENSOR_SPEED_STANDARD);
    Log.d(TAG, "Sensor damping preference " + dampingPreference);
    Log.d(TAG, "Sensor speed preference " + speedPreference);
    int dampingIndex = 0;
    if (ApplicationConstants.SENSOR_DAMPING_HIGH.equals(dampingPreference)) {
      dampingIndex = 1;
    } else if (ApplicationConstants.SENSOR_DAMPING_EXTRA_HIGH.equals(dampingPreference)) {
      dampingIndex = 2;
    } else if (ApplicationConstants.SENSOR_DAMPING_REALLY_HIGH.equals(dampingPreference)) {
      dampingIndex = 3;
    }
    int sensorSpeed = SensorManager.SENSOR_DELAY_GAME;
    if (ApplicationConstants.SENSOR_SPEED_SLOW.equals(speedPreference)) {
      sensorSpeed = SensorManager.SENSOR_DELAY_NORMAL;
    } else if (ApplicationConstants.SENSOR_SPEED_HIGH.equals(speedPreference)) {
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
      if (sharedPreferences.getBoolean(ApplicationConstants.SHARED_PREFERENCE_EXPERIMENTAL_USE_GYRO,
          false)) {
        manager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        manager.registerListener(
            accuracyReporter, manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL);
        manager.registerListener(
            accuracyReporter, manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            SensorManager.SENSOR_DELAY_NORMAL);
      } else {
        manager.registerListener(accelerometerSmoother,
                               SensorManager.SENSOR_ACCELEROMETER,
                               sensorSpeed);
        manager.registerListener(compassSmoother,
                                 SensorManager.SENSOR_MAGNETIC_FIELD,
                                 sensorSpeed);
      }
    }
    Log.d(TAG, "Registered sensor listener");
  }

  @Override
  public void stop() {
    Log.d(TAG, "Unregistering sensor listeners");
    manager.unregisterListener(accelerometerSmoother);
    manager.unregisterListener(compassSmoother);
    manager.unregisterListener(this);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (ApplicationConstants.SENSOR_DAMPING_PREF_KEY.equals(key) || ApplicationConstants.SENSOR_SPEED_PREF_KEY.equals(key)
        || ApplicationConstants.SHARED_PREFERENCE_EXPERIMENTAL_USE_GYRO.equals(key)) {
      Log.d(TAG, "User sensor preferences changed - restarting sensor controllers");
      stop();
      start();
    }
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (event.sensor != rotationSensor) {
      return;
    }
    model.setPhoneSensorValues(event.values);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // Ignore
  }
}
