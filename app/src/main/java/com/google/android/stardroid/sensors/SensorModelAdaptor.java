package com.google.android.stardroid.sensors;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import com.google.android.stardroid.control.AstronomerModel;

import javax.inject.Inject;

/**
 * Connects the rotation vector to the model code.
 */
public class SensorModelAdaptor implements SensorEventListener {
  private AstronomerModel model;

  @Inject
  SensorModelAdaptor(AstronomerModel model) {
    this.model = model;
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    // do something with the model
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // Do nothing.
  }
}
