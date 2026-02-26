package com.google.android.stardroid.activities;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger;
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.activities.util.EdgeToEdgeFixer;
import com.google.android.stardroid.activities.util.SensorAccuracyDecoder;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.util.Toaster;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class CompassCalibrationActivity extends InjectableActivity implements SensorEventListener, ActivityLightLevelChanger.NightModeable {
  public static final String HIDE_CHECKBOX = "hide checkbox";
  public static final String DONT_SHOW_CALIBRATION_DIALOG = "no calibration dialog";
  public static final String AUTO_DISMISSABLE = "auto dismissable";
  private static final String TAG = MiscUtil.getTag(CompassCalibrationActivity.class);
  private Sensor magneticSensor;
  private CheckBox checkBoxView;
  private WebView webView;
  private boolean nightMode = false;
  private int lastAccuracy = -1;

  @Inject @Nullable SensorManager sensorManager;
  @Inject SensorAccuracyDecoder accuracyDecoder;
  @Inject SharedPreferences sharedPreferences;
  @Inject Analytics analytics;
  @Inject Toaster toaster;

  @Inject
  ActivityLightLevelManager lightLevelManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    DaggerCompassCalibrationComponent.builder()
        .applicationComponent(getApplicationComponent())
        .compassCalibrationModule(new CompassCalibrationModule(this)).build().inject(this);

    setContentView(R.layout.activity_compass_calibration);
    EdgeToEdgeFixer.applyEdgeToEdgeFixForActionBarActivity(this);
    webView = findViewById(R.id.compass_calib_activity_webview);
    android.webkit.WebSettings webSettings = webView.getSettings();
    webSettings.setJavaScriptEnabled(true);
    webView.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageFinished(WebView view, String url) {
        applyNightMode();
      }
    });
    webView.loadUrl("file:///android_asset/html/animated_gif_wrapper.html");

    checkBoxView = findViewById(R.id.compass_calib_activity_donotshow);
    boolean dialogUserInitiated = getIntent().getBooleanExtra(HIDE_CHECKBOX, false);
    String whatToDoText;
    String videoUrl = "https://www.youtube.com/watch?v=-Uq7AmSAjt8";
    if (dialogUserInitiated) {
      checkBoxView.setVisibility(View.GONE);
      TextView reasonText = findViewById(R.id.compass_calib_activity_explain_why);
      reasonText.setText(R.string.compass_calibration_activity_user_heading);
      whatToDoText = getString(R.string.compass_calib_what_to_do_user, videoUrl);
    } else {
      checkBoxView.setVisibility(View.VISIBLE);
      TextView reasonText = findViewById(R.id.compass_calib_activity_explain_why);
      reasonText.setText(R.string.compass_calibration_activity_warning);
      whatToDoText = getString(R.string.compass_calib_what_to_do, videoUrl);
    }
    TextView explanationText = findViewById(R.id.compass_calib_what_to_do);
    explanationText.setText(Html.fromHtml(whatToDoText, Html.FROM_HTML_MODE_LEGACY));

    if (sensorManager != null) {
      magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }
    if (magneticSensor == null) {
      ((TextView) findViewById(R.id.compass_calib_activity_compass_accuracy)).setText(
          getString(R.string.sensor_absent));
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    lightLevelManager.onResume();
    if (magneticSensor != null && sensorManager != null) {
      sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    lightLevelManager.onPause();
    if (sensorManager != null) {
      sensorManager.unregisterListener(this);
    }
    if (checkBoxView.isChecked()) {
      sharedPreferences.edit().putBoolean(DONT_SHOW_CALIBRATION_DIALOG, true).apply();
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
    lastAccuracy = accuracy;
    TextView accuracyTextView = findViewById(R.id.compass_calib_activity_compass_accuracy);
    String accuracyText = accuracyDecoder.getTextForAccuracy(accuracy);
    accuracyTextView.setText(accuracyText);
    int color = nightMode ? accuracyDecoder.getNightColorForAccuracy(accuracy)
                          : accuracyDecoder.getColorForAccuracy(accuracy);
    accuracyTextView.setTextColor(color);
    if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        && getIntent().getBooleanExtra(AUTO_DISMISSABLE, false)) {
      toaster.toastLong(R.string.sensor_accuracy_high);
      this.finish();
    }
  }

  @Override
  public void setNightMode(boolean nightMode) {
    this.nightMode = nightMode;
    applyNightMode();
  }

  private static final int NIGHT_TEXT_COLOR = 0xFFCC4444;
  private static final int NIGHT_LINK_COLOR = 0xFFCC6666;
  private static final int DAY_LINK_COLOR = 0xFF33B5E5;  // Holo default link color

  private void applyNightMode() {
    if (webView == null) return;
    if (nightMode) {
      webView.evaluateJavascript("document.body.classList.add('night-mode')", null);
    } else {
      webView.evaluateJavascript("document.body.classList.remove('night-mode')", null);
    }
    int textColor = nightMode ? NIGHT_TEXT_COLOR : Color.WHITE;
    int[] viewIdsToColor = {
        R.id.compass_calib_activity_explain_why,
        R.id.compass_calib_activity_heading_label,
        R.id.compass_calib_activity_donotshow,
        R.id.compass_calib_what_to_do,
        R.id.compass_calib_activity_ok_button
    };
    for (int id : viewIdsToColor) {
      TextView tv = findViewById(id);
      if (tv != null) tv.setTextColor(textColor);
    }
    TextView whatToDo = findViewById(R.id.compass_calib_what_to_do);
    if (whatToDo != null) {
      whatToDo.setLinkTextColor(nightMode ? NIGHT_LINK_COLOR : DAY_LINK_COLOR);
    }
    if (lastAccuracy != -1) {
      int accuracyColor = nightMode ? accuracyDecoder.getNightColorForAccuracy(lastAccuracy)
                                    : accuracyDecoder.getColorForAccuracy(lastAccuracy);
      TextView accuracyTextView = findViewById(R.id.compass_calib_activity_compass_accuracy);
      if (accuracyTextView != null) accuracyTextView.setTextColor(accuracyColor);
    }
  }

  public void onOkClicked(View unused) {
    finish();
  }

  @Override
  public void onStart() {
    super.onStart();
    View rootView = findViewById(android.R.id.content);
    EdgeToEdgeFixer.applyTopPaddingForActionBar(this, rootView);
  }
}
