// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.stardroid.layers.LayerManager;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.AnalyticsInterface;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.util.PreferenceChangeAnalyticsTracker;
import com.google.android.stardroid.views.PreferencesButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * The main Stardroid Application class.
 *
 * @author John Taylor
 */
public class StardroidApplication extends Application {
  private static final String TAG = MiscUtil.getTag(StardroidApplication.class);
  private static final String PREVIOUS_APP_VERSION_PREF = "previous_app_version";
  private static final String NONE = "Clean install";
  private static final String UNKNOWN = "Unknown previous version";

  @Inject SharedPreferences preferences;
  // We keep a reference to this just to start it initializing.
  @Inject LayerManager layerManager;
  @Inject AnalyticsInterface analytics;
  @Inject SensorManager sensorManager;

  // We need to maintain references to this object to keep it from
  // getting gc'd.
  @Inject PreferenceChangeAnalyticsTracker preferenceChangeAnalyticsTracker;
  private ApplicationComponent component;

  @Override
  public void onCreate() {
    Log.d(TAG, "StardroidApplication: onCreate");
    super.onCreate();

    component = DaggerApplicationComponent.builder()
        .applicationModule(new ApplicationModule(this))
        .build();
    component.inject(this);

    Log.i(TAG, "OS Version: " + android.os.Build.VERSION.RELEASE
            + "(" + android.os.Build.VERSION.SDK_INT + ")");
    String versionName = getVersionName();
    Log.i(TAG, "Sky Map version " + versionName + " build " + getVersion());

    // This populates the default values from the preferences XML file. See
    // {@link DefaultValues} for more details.
    PreferenceManager.setDefaultValues(this, R.xml.preference_screen, false);

    setUpAnalytics(versionName);

    performFeatureCheck();

    Log.d(TAG, "StardroidApplication: -onCreate");
  }

  public ApplicationComponent getApplicationComponent() {
    return component;
  }

  private void setUpAnalytics(String versionName) {
    analytics.setEnabled(preferences.getBoolean(Analytics.PREF_KEY, true));

    // Ugly hack since this isn't injectable
    PreferencesButton.setAnalytics(analytics);

    String previousVersion = preferences.getString(PREVIOUS_APP_VERSION_PREF, NONE);
    boolean newUser = false;
    if (previousVersion.equals(NONE)) {
      // It's possible a previous version exists, it's just that it wasn't a recent enough
      // version to have set PREVIOUS_APP_VERSION_PREF.  If so, we should see that the TOS
      // have been accepted.
      String oldPreviousVersionKey = "read_tos";
      if (preferences.contains(oldPreviousVersionKey)) {
        previousVersion = UNKNOWN;
      } else {
        // Best guess that this is the first every run of a new user.
        // Could also be someone with a new device.
        newUser = true;
      }
    }
    analytics.setUserProperty(AnalyticsInterface.NEW_USER, Boolean.toString(newUser));

    preferences.edit().putString(PREVIOUS_APP_VERSION_PREF, versionName).commit();
    if (!previousVersion.equals(versionName)) {
      // It's either an upgrade or a new installation
      Log.d(TAG, "New installation: version " + versionName);
      // No need to track any more - it's automatic in Firebase.
    }

    // It will be interesting to see *when* people use Sky Map.
    Bundle b = new Bundle();
    b.putInt(Analytics.START_EVENT_HOUR, Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    analytics.trackEvent(Analytics.START_EVENT, b);

    preferences.registerOnSharedPreferenceChangeListener(preferenceChangeAnalyticsTracker);
  }

  @Override
  public void onTerminate() {
    super.onTerminate();
    analytics.setEnabled(false);
  }

  /**
   * Returns the version string for Sky Map.
   */
  public String getVersionName() {
    // TODO(jontayler): update to use the info created by gradle.
    PackageManager packageManager = getPackageManager();
    try {
      PackageInfo info = packageManager.getPackageInfo(this.getPackageName(), 0);
      return info.versionName;
    } catch (NameNotFoundException e) {
      Log.e(TAG, "Unable to obtain package info");
      return "Unknown";
    }
  }

  /**
   * Returns the build number for Sky Map.
   */
  public int getVersion() {
    PackageManager packageManager = getPackageManager();
    try {
      PackageInfo info = packageManager.getPackageInfo(this.getPackageName(), 0);
      return info.versionCode;
    } catch (NameNotFoundException e) {
      Log.e(TAG, "Unable to obtain package info");
      return -1;
    }
  }

  /**
   * Returns either the name of the sensor or a string version of the sensor type id, depending
   * on the supported OS level along with some context.
   */
  public static String getSafeNameForSensor(Sensor sensor) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
      return "Sensor type: " + sensor.getStringType() + ": " + sensor.getType();
    } else {
      return "Sensor type: " + sensor.getType();
    }
  }

  /**
   * Check what features are available to this phone and report back to analytics
   * so we can judge when to add/drop support.
   */
  private void performFeatureCheck() {
    if (sensorManager == null) {
      Log.e(TAG, "No sensor manager");
      analytics.setUserProperty(Analytics.DEVICE_SENSORS, Analytics.DEVICE_SENSORS_NONE);
      return;
    }
    // Reported available sensors
    List<String> reportedSensors = new ArrayList<>();
    if (hasDefaultSensor(Sensor.TYPE_ACCELEROMETER)) {
      reportedSensors.add(Analytics.DEVICE_SENSORS_ACCELEROMETER);
    }
    if (hasDefaultSensor(Sensor.TYPE_GYROSCOPE)) {
      reportedSensors.add(Analytics.DEVICE_SENSORS_GYRO);
    }
    if (hasDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)) {
      reportedSensors.add(Analytics.DEVICE_SENSORS_MAGNETIC);
    }
    if (hasDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)) {
      reportedSensors.add(Analytics.DEVICE_SENSORS_ROTATION);
    }

    // TODO: Change to String.join once we're at API > 26
    analytics.setUserProperty(
            Analytics.DEVICE_SENSORS, TextUtils.join("|", reportedSensors));

    // Check for a particularly strange combo - it would be weird to have a rotation sensor
    // but no accelerometer or magnetic field sensor
    boolean hasRotationSensor = false;
    if (hasDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)) {
      if (hasDefaultSensor(Sensor.TYPE_ACCELEROMETER) && hasDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
              && hasDefaultSensor(Sensor.TYPE_GYROSCOPE)) {
        hasRotationSensor = true;
      } else if (hasDefaultSensor(Sensor.TYPE_ACCELEROMETER) && hasDefaultSensor(
              Sensor.TYPE_MAGNETIC_FIELD)) {
        // Even though it allegedly has the rotation vector sensor too many gyro-less phones
        // lie about this, so put these devices on the 'classic' sensor code for now.
        hasRotationSensor = false;
      }
    }

    // Enable Gyro if available and user hasn't already disabled it.
    if (!preferences.contains(ApplicationConstants.SHARED_PREFERENCE_DISABLE_GYRO)) {
      preferences.edit().putBoolean(
          ApplicationConstants.SHARED_PREFERENCE_DISABLE_GYRO, !hasRotationSensor).apply();
    }

    // Lastly a dump of all the sensors.
    Log.d(TAG, "All sensors:");
    List<Sensor> allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
    Set<String> sensorTypes = new HashSet<>();
    for (Sensor sensor : allSensors) {
      Log.i(TAG, sensor.getName());
      sensorTypes.add(getSafeNameForSensor(sensor));
    }
    Log.d(TAG, "All sensors summary:");
    for (String sensorType : sensorTypes) {
      Log.i(TAG, sensorType);
    }
  }

  private boolean hasDefaultSensor(int sensorType) {
    if (sensorManager == null) {
      return false;
    }
    Sensor sensor = sensorManager.getDefaultSensor(sensorType);
    if (sensor == null) {
      return false;
    }
    SensorEventListener dummy = new SensorEventListener() {
      @Override
      public void onSensorChanged(SensorEvent event) {
        // Nothing
      }

      @Override
      public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing
      }
    };
    boolean success = sensorManager.registerListener(
        dummy, sensor, SensorManager.SENSOR_DELAY_UI);
    if (!success) {
      analytics.setUserProperty(Analytics.SENSOR_LIAR, "true");
    }
    sensorManager.unregisterListener(dummy);
    return success;
  }
}
