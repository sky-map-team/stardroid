package com.google.android.stardroid.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.google.android.stardroid.activities.CompassCalibrationActivity
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.util.MiscUtil.getTag
import javax.inject.Inject

/**
 * Monitors the compass accuracy and if it is not medium or high warns the user.
 * Created by johntaylor on 4/24/16.
 */
class SensorAccuracyMonitor @Inject internal constructor(
  sensorManager: SensorManager?, context: Context,
  sharedPreferences: SharedPreferences,
  toaster: Toaster
) : SensorEventListener {
  private val sensorManager: SensorManager?
  private val compassSensor: Sensor?
  private val context: Context
  private val sharedPreferences: SharedPreferences
  private val toaster: Toaster
  private var started = false
  private var hasReading = false

  /**
   * Starts monitoring.
   */
  fun start() {
    if (started) {
      return
    }
    Log.d(TAG, "Starting monitoring compass accuracy")
    if (compassSensor != null) {
      (sensorManager ?: return).registerListener(this, compassSensor, SensorManager.SENSOR_DELAY_UI)
    }
    started = true
  }

  /**
   * Stops monitoring.  It's important this is called to disconnect from the sensors and
   * ensure the app does not needlessly consume power when in the background.
   */
  fun stop() {
    Log.d(TAG, "Stopping monitoring compass accuracy")
    started = false
    hasReading = false
    (sensorManager ?: return).unregisterListener(this)
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (!hasReading) {
      onAccuracyChanged(event.sensor, event.accuracy)
    }
  }

  override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    hasReading = true
    if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH
      || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
    ) {
      return  // OK
    }
    Log.d(TAG, "Compass accuracy insufficient")
    val nowMillis = System.currentTimeMillis()
    val lastWarnedMillis = sharedPreferences.getLong(LAST_CALIBRATION_WARNING_PREF_KEY, 0)
    if (nowMillis - lastWarnedMillis < MIN_INTERVAL_BETWEEN_WARNINGS) {
      Log.d(TAG, "...but too soon to warn again")
      return
    }
    sharedPreferences.edit().putLong(LAST_CALIBRATION_WARNING_PREF_KEY, nowMillis).apply()
    val dontShowDialog = sharedPreferences.getBoolean(
      CompassCalibrationActivity.DONT_SHOW_CALIBRATION_DIALOG, false
    )
    if (dontShowDialog) {
      toaster.toastLong("Inaccurate compass - please calibrate")
    } else {
      val intent = Intent(context, CompassCalibrationActivity::class.java)
      intent.putExtra(CompassCalibrationActivity.HIDE_CHECKBOX, false)
      intent.putExtra(CompassCalibrationActivity.AUTO_DISMISSABLE, true)
      context.startActivity(intent)
    }
  }

  companion object {
    private val TAG = getTag(SensorAccuracyMonitor::class.java)
    private const val LAST_CALIBRATION_WARNING_PREF_KEY = "Last calibration warning time"
    private const val MIN_INTERVAL_BETWEEN_WARNINGS = 180 * TimeConstants.MILLISECONDS_PER_SECOND
  }

  init {
    Log.d(TAG, "Creating new accuracy monitor")
    this.sensorManager = sensorManager
    this.context = context
    this.sharedPreferences = sharedPreferences
    this.toaster = toaster
    compassSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
  }
}