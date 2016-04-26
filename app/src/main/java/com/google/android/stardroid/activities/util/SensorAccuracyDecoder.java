package com.google.android.stardroid.activities.util;

import android.content.Context;
import android.hardware.SensorManager;

import com.google.android.stardroid.R;

import javax.inject.Inject;

/**
 * Created by johntaylor on 4/24/16.
 */
public class SensorAccuracyDecoder {
  private Context context;

  @Inject
  public SensorAccuracyDecoder(Context context) {
    this.context = context;
  }

  public String getTextForAccuracy(int accuracy) {
    String accuracyTxt = context.getString(R.string.sensor_accuracy_unknown);
    switch (accuracy) {
      case SensorManager.SENSOR_STATUS_UNRELIABLE:
        accuracyTxt = context.getString(R.string.sensor_accuracy_unreliable);
        break;
      case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
        accuracyTxt = context.getString(R.string.sensor_accuracy_low);
        break;
      case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
        accuracyTxt = context.getString(R.string.sensor_accuracy_medium);
        break;
      case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
        accuracyTxt = context.getString(R.string.sensor_accuracy_high);
        break;
      case SensorManager.SENSOR_STATUS_NO_CONTACT:
        accuracyTxt = context.getString(R.string.sensor_accuracy_nocontact);
        break;
    }
    return accuracyTxt;
  }
}
