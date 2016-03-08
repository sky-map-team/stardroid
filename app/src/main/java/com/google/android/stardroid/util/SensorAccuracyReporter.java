package com.google.android.stardroid.util;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Logs the reported accuracy of the compass.
 */
public class SensorAccuracyReporter implements SensorEventListener {
  private String TAG = MiscUtil.getTag(this);
  private Analytics analytics;
  private long startTimeMs;
  private Set<Sensor> highAccuracyAchievedForSensor = new HashSet<>();
  private Map<Sensor, Long> timeSinceLastUpdateForSensor = new HashMap<>();

  public SensorAccuracyReporter(Analytics analytics) {
    this.analytics = analytics;
    this.startTimeMs = System.currentTimeMillis();
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    // Ignore.
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    long currentTimeMs = System.currentTimeMillis();
    if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
      // Record time to high accuracy.
      int boundedElapsedTimeMs = boundedInt(currentTimeMs - startTimeMs);
      if (!highAccuracyAchievedForSensor.contains(sensor)) {
        Log.d(TAG, "Sensor " + sensor.getName() + " achieved high accuracy at time "
            + boundedElapsedTimeMs + " milliseconds");
        highAccuracyAchievedForSensor.add(sensor);
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.HIGH_SENSOR_ACCURACY_ACHIEVED,
            Analytics.getSafeNameForSensor(sensor), boundedElapsedTimeMs);
      }
    }

    int elapsedTimeSinceLastUpdateForThisSensor = boundedInt(currentTimeMs
        - getLastUpdateTimeForSensorAndUpdate(sensor, currentTimeMs));

    Log.d(TAG, sensor.getName() + " accuracy now " + accuracy + " after "
        + elapsedTimeSinceLastUpdateForThisSensor + " elapsed milliseconds");

    analytics.trackEvent(
        Analytics.SENSOR_CATEGORY,
        Analytics.SENSOR_ACCURACY_CHANGED + ":" + accuracyToString(accuracy),
        Analytics.getSafeNameForSensor(sensor), elapsedTimeSinceLastUpdateForThisSensor);
  }

  private int boundedInt(long value) {
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE :
        (int) value;
  }

  private String accuracyToString(int acc) {
    switch (acc) {
      case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
        return "high";
      case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
        return "low";
      case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
        return "medium";
      case SensorManager.SENSOR_STATUS_NO_CONTACT:
        return "nocontact";
      case SensorManager.SENSOR_STATUS_UNRELIABLE:
        return "unreliable";
      default:
        return "unknown";
    }
  }

  private long getLastUpdateTimeForSensorAndUpdate(Sensor sensor, long currentTimeMs) {
    long time;
    if (timeSinceLastUpdateForSensor.containsKey(sensor)) {
      time = timeSinceLastUpdateForSensor.get(sensor);
    } else {
      time = startTimeMs;
    }
    timeSinceLastUpdateForSensor.put(sensor, currentTimeMs);
    return time;
  }
}
