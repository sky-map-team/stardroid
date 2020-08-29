package com.google.android.stardroid.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.google.android.stardroid.activities.CompassCalibrationActivity;
import com.google.android.stardroid.base.TimeConstants;

import javax.inject.Inject;

/**
 * Monitors the compass accuracy and if it is not medium or high warns the user.
 * Created by johntaylor on 4/24/16.
 */
public class SensorAccuracyMonitor implements SensorEventListener {
  private static final String TAG = MiscUtil.getTag(SensorAccuracyMonitor.class);
  private static final String LAST_CALIBRATION_WARNING_PREF_KEY = "Last calibration warning time";

  private SensorManager sensorManager;
  private Sensor compassSensor;
  private Context context;
  private SharedPreferences sharedPreferences;
  private Toaster toaster;

  @Inject
  SensorAccuracyMonitor(
      SensorManager sensorManager, Context context, SharedPreferences sharedPreferences,
      Toaster toaster) {
    Log.d(TAG, "Creating new accuracy monitor");
    this.sensorManager = sensorManager;
    this.context = context;
    this.sharedPreferences = sharedPreferences;
    this.toaster = toaster;
    compassSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
  }

  private boolean started = false;
  private boolean hasReading = false;

  /**
   * Starts monitoring.
   */
  public void start() {
    if (started) {
      return;
    }
    Log.d(TAG, "Starting monitoring compass accuracy");
    if (compassSensor != null) {
      sensorManager.registerListener(this, compassSensor, SensorManager.SENSOR_DELAY_UI);
    }
    started = true;
  }

  /**
   * Stops monitoring.  It's important this is called to disconnect from the sensors and
   * ensure the app does not needlessly consume power when in the background.
   */
  public void stop() {
    Log.d(TAG, "Stopping monitoring compass accuracy");
    started = false;
    hasReading = false;
    sensorManager.unregisterListener(this);
  }


  @Override
  public void onSensorChanged(SensorEvent event) {
    if (!hasReading) {
      onAccuracyChanged(event.sensor, event.accuracy);
    }
  }

  private static final long MIN_INTERVAL_BETWEEN_WARNINGS =
      180 * TimeConstants.MILLISECONDS_PER_SECOND;

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    hasReading = true;
    if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
      return;  // OK
    }
    Log.d(TAG, "Compass accuracy insufficient");
    long nowMillis = System.currentTimeMillis();
    long lastWarnedMillis = sharedPreferences.getLong(LAST_CALIBRATION_WARNING_PREF_KEY, 0);
    if (nowMillis - lastWarnedMillis < MIN_INTERVAL_BETWEEN_WARNINGS) {
      Log.d(TAG, "...but too soon to warn again");
      return;
    }
    sharedPreferences.edit().putLong(LAST_CALIBRATION_WARNING_PREF_KEY, nowMillis).apply();
    boolean dontShowDialog = sharedPreferences.getBoolean(
        CompassCalibrationActivity.DONT_SHOW_CALIBRATION_DIALOG, false);
    if (dontShowDialog) {
      toaster.toastLong("Inaccurate compass - please calibrate");
    } else {
      Intent intent = new Intent(context, CompassCalibrationActivity.class);
      intent.putExtra(CompassCalibrationActivity.HIDE_CHECKBOX, false);
      intent.putExtra(CompassCalibrationActivity.AUTO_DISMISSABLE, true);
      context.startActivity(intent);
    }
  }
}
