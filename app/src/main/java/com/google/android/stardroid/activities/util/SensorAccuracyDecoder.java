package com.google.android.stardroid.activities.util;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.SensorManager;

import com.google.android.stardroid.R;

import javax.inject.Inject;

/**
 * Created by johntaylor on 4/24/16.
 */
public class SensorAccuracyDecoder {
  private final Resources resources;
  private Context context;

  @Inject
  public SensorAccuracyDecoder(Context context) {
    this.context = context;
    this.resources = context.getResources();
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

  public int getColorForAccuracy(int accuracy) {
    switch (accuracy) {
      case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
        return resources.getColor(R.color.status_warning);
      case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
        return resources.getColor(R.color.status_ok);
      case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
        return resources.getColor(R.color.status_good);
      case SensorManager.SENSOR_STATUS_UNRELIABLE:
      case SensorManager.SENSOR_STATUS_NO_CONTACT:
      default:
        return resources.getColor(R.color.status_bad);
    }
  }

  /** Red-shifted accuracy colors for night mode, preserving the good/bad brightness hierarchy. */
  public int getNightColorForAccuracy(int accuracy) {
    switch (accuracy) {
      case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
        return resources.getColor(R.color.night_status_good);
      case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
        return resources.getColor(R.color.night_status_ok);
      case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
        return resources.getColor(R.color.night_status_warning);
      case SensorManager.SENSOR_STATUS_UNRELIABLE:
      case SensorManager.SENSOR_STATUS_NO_CONTACT:
      default:
        return resources.getColor(R.color.night_status_bad);
    }
  }
}
