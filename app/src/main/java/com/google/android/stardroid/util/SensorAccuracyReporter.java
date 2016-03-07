package com.google.android.stardroid.util;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

/**
 * Logs the reported accuracy of the compass.
 */
public class SensorAccuracyReporter implements SensorEventListener {
  private String TAG = MiscUtil.getTag(this);
  private Analytics analytics;
  public SensorAccuracyReporter(Analytics analytics) {
    this.analytics = analytics;
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    // Ignore.
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    Log.d(TAG, sensor.getStringType() + " acccuracy now " + accuracy);
  }
}
