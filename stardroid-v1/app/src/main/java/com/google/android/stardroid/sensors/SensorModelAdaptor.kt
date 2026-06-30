/*
 * Copyright (c) 2026 Penterakt LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import com.google.android.stardroid.control.AstronomerModel
import javax.inject.Inject

/**
 * Connects the rotation vector to the model code.
 */
class SensorModelAdaptor @Inject internal constructor(private val model: AstronomerModel) :
  SensorEventListener {
  override fun onSensorChanged(event: SensorEvent) {
    // do something with the model
  }

  override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    // Do nothing.
  }
}