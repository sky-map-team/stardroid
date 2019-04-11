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

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * Sets the direction of view from the orientation sensors.
 *
 * @author John Taylor
 */
public class SensorOrientationController extends AbstractController
        implements SensorEventListener {

  private final static String TAG = MiscUtil.getTag(SensorOrientationController.class);
  private SensorManager manager;
  private Sensor rotationSensor;
    private Sensor geomagneticRotationSensor;
  private SharedPreferences sharedPreferences;

  @Inject
  SensorOrientationController(SensorManager manager, SharedPreferences sharedPreferences) {
    this.manager = manager;
    this.rotationSensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          this.geomagneticRotationSensor = manager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
      }
    this.sharedPreferences = sharedPreferences;
  }

  @Override
  public void start() {
    if (manager != null) {
      if (!sharedPreferences.getBoolean(ApplicationConstants.SHARED_PREFERENCE_DISABLE_GYRO,
              false)) {
        Log.d(TAG, "Using rotation sensor");
        manager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          Log.d(TAG, "Using geomagnetic_rotation sensor");
          manager.registerListener(this, geomagneticRotationSensor, SensorManager.SENSOR_DELAY_FASTEST);
      }
    }
    Log.d(TAG, "Registered sensor listener");
  }

  @Override
  public void stop() {
    manager.unregisterListener(this);
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
      if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
          model.setPhoneSensorValues(event.values);
      } else if (event.sensor.getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
          model.setPhoneSensorValues(event.values);
      } else {
          Log.e(TAG, "Unknown Sensor readings");
      }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // Ignore
  }
}
