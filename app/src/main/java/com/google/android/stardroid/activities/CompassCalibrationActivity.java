package com.google.android.stardroid.activities;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.CheckBox;
import android.widget.TextView;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.util.SensorAccuracyDecoder;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.util.Toaster;

import javax.inject.Inject;

public class CompassCalibrationActivity extends InjectableActivity implements SensorEventListener {
  public static final String HIDE_CHECKBOX = "hide checkbox";
  public static final String DONT_SHOW_CALIBRATION_DIALOG = "no calibration dialog";
  public static final String AUTO_DISMISSABLE = "auto dismissable";
  private static final String TAG = MiscUtil.getTag(CompassCalibrationActivity.class);
  private Sensor magneticSensor;
  private CheckBox checkBoxView;

  @Inject SensorManager sensorManager;
  @Inject SensorAccuracyDecoder accuracyDecoder;
  @Inject SharedPreferences sharedPreferences;
  @Inject Analytics analytics;
  @Inject Toaster toaster;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    DaggerCompassCalibrationComponent.builder()
        .applicationComponent(getApplicationComponent())
        .compassCalibrationModule(new CompassCalibrationModule(this)).build().inject(this);

    setContentView(R.layout.activity_compass_calibration);
    WebView web = (WebView) findViewById(R.id.compass_calib_activity_webview);
    web.loadUrl("file:///android_asset/html/how_to_calibrate.html");

    checkBoxView = (CheckBox) findViewById(R.id.compass_calib_activity_donotshow);
    boolean hideCheckbox = getIntent().getBooleanExtra(HIDE_CHECKBOX, false);
    if (hideCheckbox) {
      checkBoxView.setVisibility(View.GONE);
      View reasonText = findViewById(R.id.compass_calib_activity_explain_why);
      reasonText.setVisibility(View.GONE);
    }
    magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    if (magneticSensor == null) {
      ((TextView) findViewById(R.id.compass_calib_activity_compass_accuracy)).setText(
          getString(R.string.sensor_absent));
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (magneticSensor != null) {
      sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    sensorManager.unregisterListener(this);
    if (checkBoxView.isChecked()) {
      sharedPreferences.edit().putBoolean(DONT_SHOW_CALIBRATION_DIALOG, true).commit();
    }
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
    TextView accuracyTextView = findViewById(R.id.compass_calib_activity_compass_accuracy);
    String accuracyText = accuracyDecoder.getTextForAccuracy(accuracy);
    accuracyTextView.setText(accuracyText);
    accuracyTextView.setTextColor(accuracyDecoder.getColorForAccuracy(accuracy));
    if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        && getIntent().getBooleanExtra(AUTO_DISMISSABLE, false)) {
      toaster.toastLong(R.string.sensor_accuracy_high);
      this.finish();
    }
  }

  public void onOkClicked(View unused) {
    finish();
  }

  @Override
  public void onStart() {
    super.onStart();
  }
}
