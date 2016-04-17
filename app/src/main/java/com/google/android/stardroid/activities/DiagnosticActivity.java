package com.google.android.stardroid.activities;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

public class DiagnosticActivity extends Activity implements SensorEventListener {
  private static final String TAG = MiscUtil.getTag(DiagnosticActivity.class);

  @Inject Analytics analytics;
  @Inject StardroidApplication app;
  @Inject SensorManager sensorManager;
  private Sensor accelSensor;
  private Sensor magSensor;
  private Sensor gyroSensor;
  private Sensor lightSensor;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    DaggerDiagnosticActivityComponent.builder().applicationComponent(
      ((StardroidApplication) getApplication()).getApplicationComponent()).build().inject(this);
    setContentView(R.layout.activity_diagnostic);
  }

  @Override
  public void onStart() {
    super.onStart();
    analytics.trackPageView(Analytics.DIAGNOSTICS_ACTIVITY);

    setText(R.id.diagnose_phone_txt, Build.MODEL);
    String androidVersion = String.format(Build.VERSION.RELEASE + " (%d)", Build.VERSION.SDK_INT);
    setText(R.id.diagnose_android_version_txt, androidVersion);

    String skyMapVersion = String.format(
        app.getVersionName() + " (%d)", app.getVersion());
    setText(R.id.diagnose_skymap_version_txt, skyMapVersion);
  }

  @Override
  public void onResume() {
    super.onResume();
    accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    if (accelSensor == null) {
      setText(R.id.diagnose_accelerometer_accuracy_txt, getString(R.string.sensor_absent));
    } else {
      sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }
    magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    if (magSensor == null) {
      setText(R.id.diagnose_compass_accuracy_txt, getString(R.string.sensor_absent));
    } else {
      sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }
    gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    if (gyroSensor == null) {
      setText(R.id.diagnose_gyro_accuracy_txt, getString(R.string.sensor_absent));
    } else {
      sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }
    lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
    if (lightSensor == null) {
      setText(R.id.diagnose_light_accuracy_txt, getString(R.string.sensor_absent));
    } else {
      sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    sensorManager.unregisterListener(this);
  }

  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    knownSensorAccuracies.add(sensor);
    Log.d(TAG, "set size" + knownSensorAccuracies.size());
    int accuracyViewId;
    if (sensor == accelSensor) {
      accuracyViewId = R.id.diagnose_accelerometer_accuracy_txt;
    } else if (sensor == magSensor) {
      accuracyViewId = R.id.diagnose_compass_accuracy_txt;
    } else if (sensor == gyroSensor) {
      accuracyViewId = R.id.diagnose_gyro_accuracy_txt;
    } else if (sensor == lightSensor) {
      accuracyViewId = R.id.diagnose_light_accuracy_txt;
    } else {
      Log.e(TAG, "Receiving accuracy change for unknown sensor " + sensor);
      return;
    }
    String accuracyTxt = getString(R.string.sensor_accuracy_unknown);
    switch (accuracy) {
      case SensorManager.SENSOR_STATUS_UNRELIABLE:
        accuracyTxt = getString(R.string.sensor_accuracy_unreliable);
        break;
      case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
        accuracyTxt = getString(R.string.sensor_accuracy_low);
        break;
      case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
        accuracyTxt = getString(R.string.sensor_accuracy_medium);
        break;
      case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
        accuracyTxt = getString(R.string.sensor_accuracy_high);
        break;
      case SensorManager.SENSOR_STATUS_NO_CONTACT:
        accuracyTxt = getString(R.string.sensor_accuracy_nocontact);
        break;
    }
    setText(accuracyViewId, accuracyTxt);
  }

  private Set<Sensor> knownSensorAccuracies = new HashSet<>();
  
  public void onSensorChanged(SensorEvent event) {
    Sensor sensor = event.sensor;
    if (!knownSensorAccuracies.contains(sensor)) {
      onAccuracyChanged(sensor, event.accuracy);
    }
    int valuesViewId;
    if (sensor == accelSensor) {
      valuesViewId = R.id.diagnose_accelerometer_values_txt;
    } else if (sensor == magSensor) {
      valuesViewId = R.id.diagnose_compass_values_txt;
    } else if (sensor == gyroSensor) {
      valuesViewId = R.id.diagnose_gyro_values_txt;
    } else if (sensor == lightSensor) {
      valuesViewId = R.id.diagnose_light_values_txt;
    } else {
      Log.e(TAG, "Receiving values for unknown sensor " + sensor);
      return;
    }
    StringBuilder valuesText = new StringBuilder();
    for (float value : event.values) {
      valuesText.append(String.format("%.1f", value));
      valuesText.append(',');
    }
    valuesText.setLength(valuesText.length() - 1);
    setText(valuesViewId, valuesText.toString());
  }

  private void setText(int viewId, String text) {
    ((TextView) findViewById(viewId)).setText(text);
  }
}
