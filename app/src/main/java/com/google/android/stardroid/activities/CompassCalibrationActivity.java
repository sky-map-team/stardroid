package com.google.android.stardroid.activities;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.util.SensorAccuracyDecoder;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

public class CompassCalibrationActivity extends InjectableActivity implements SensorEventListener {
  public static final String HIDE_CHECKBOX = "hide checkbox";
  private static final String TAG = MiscUtil.getTag(CompassCalibrationActivity.class);
  private static final int SAMPLING_PERIOD_MS = 1000;
  private Sensor magneticSensor;

  @Inject SensorManager sensorManager;
  @Inject SensorAccuracyDecoder accuracyDecoder;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    DaggerCompassCalibrationComponent.builder()
        .applicationComponent(getApplicationComponent())
        .compassCalibrationModule(new CompassCalibrationModule(this)).build().inject(this);

    setContentView(R.layout.activity_compass_calibration);
    WebView web = (WebView) findViewById(R.id.compass_calib_activity_webview);
    web.loadUrl("file:///android_asset/html/how_to_calibrate.html");
    View checkBoxView = findViewById(R.id.compass_calib_activity_donotshow);
    boolean hideCheckbox = getIntent().getBooleanExtra(HIDE_CHECKBOX, false);
    if (hideCheckbox) {
      Log.d(TAG, "Hiding checkbox");
      checkBoxView.setVisibility(View.GONE);
    }
    magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
  }

  @Override
  public void onResume() {
    super.onResume();
    sensorManager.registerListener(this, magneticSensor, SAMPLING_PERIOD_MS);
  }

  @Override
  public void onPause() {
    super.onPause();
    sensorManager.unregisterListener(this);
  }

  private boolean accuracyReceived = false;

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (!accuracyReceived) {
      onAccuracyChanged(event.sensor, event.accuracy);
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    accuracyReceived = true;
    String accuracyText = accuracyDecoder.getTextForAccuracy(accuracy);
    ((TextView) findViewById(R.id.compass_calib_activity_compass_accuracy)).setText(accuracyText);
  }
}
