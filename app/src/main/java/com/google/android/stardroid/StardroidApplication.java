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
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.stardroid.layers.LayerManager;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.AnalyticsInterface.Slice;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.util.PreferenceChangeAnalyticsTracker;
import com.google.android.stardroid.views.PreferencesButton;

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
  @Inject Analytics analytics;
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
    analytics.setCustomVar(Slice.ANDROID_OS, Integer.toString(Build.VERSION.SDK_INT));
    analytics.setCustomVar(Slice.SKYMAP_VERSION, versionName);
    analytics.setCustomVar(Slice.DEVICE_NAME, android.os.Build.MODEL);
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
    analytics.setCustomVar(Slice.NEW_USER, Boolean.toString(newUser));

    analytics.trackPageView(Analytics.APPLICATION_CREATE);
    preferences.edit().putString(PREVIOUS_APP_VERSION_PREF, versionName).commit();
    if (!previousVersion.equals(versionName)) {
      // It's either an upgrade or a new installation
      Log.d(TAG, "New installation: version " + versionName);
      analytics.trackEvent(Analytics.INSTALL_CATEGORY, Analytics.INSTALL_EVENT + versionName,
          Analytics.PREVIOUS_VERSION + previousVersion, 1);
    }

    // It will be interesting to see *when* people use Sky Map.
    analytics.trackEvent(
        Analytics.GENERAL_CATEGORY, Analytics.START_HOUR,
        Integer.toString(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) + 'h', 0);

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
      analytics.trackEvent(
          Analytics.SENSOR_CATEGORY, Analytics.SENSOR_AVAILABILITY, "No Sensor Manager", 0);
      return;
    }
    // Minimum requirements
    if (hasDefaultSensor(Sensor.TYPE_ACCELEROMETER)) {
      if (hasDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)) {
        Log.i(TAG, "Minimal sensors available");
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_AVAILABILITY, "Minimal Sensors: Yes", 1);
      } else {
        Log.e(TAG, "No magnetic field sensor");
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_AVAILABILITY, "No Mag Field Sensor", 0);
      }
    } else {
      if (hasDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)) {
        Log.e(TAG, "No accelerometer");
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_AVAILABILITY, "No Accel Sensor", 0);
      } else {
        Log.e(TAG, "No magnetic field sensor or accelerometer");
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_AVAILABILITY, "No Mag Field/Accel Sensors", 0);
      }
    }

    // Check for a particularly strange combo - it would be weird to have a rotation sensor
    // but no accelerometer or magnetic field sensor
    boolean hasRotationSensor = false;
    if (hasDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)) {
      if (hasDefaultSensor(Sensor.TYPE_ACCELEROMETER) && hasDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
          && hasDefaultSensor(Sensor.TYPE_GYROSCOPE)) {
        hasRotationSensor = true;
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.ROT_SENSOR_AVAILABILITY, "OK - All Sensors", 1);
      } else if (hasDefaultSensor(Sensor.TYPE_ACCELEROMETER) && hasDefaultSensor(
          Sensor.TYPE_MAGNETIC_FIELD)) {
        // Even though it allegedly has the rotation vector sensor too many gyro-less phones
        // lie about this, so put these devices on the 'classic' sensor code for now.
        hasRotationSensor = false;
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.ROT_SENSOR_AVAILABILITY, "Disabled - No gyro", 1);
      } else {
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.ROT_SENSOR_AVAILABILITY, "Disabled - Missing Mag/Accel", 0);
      }
    } else {
      analytics.trackEvent(
          Analytics.SENSOR_CATEGORY, Analytics.ROT_SENSOR_AVAILABILITY, "No rotation", 0);
    }

    // Enable Gyro if available and user hasn't already disabled it.
    if (!preferences.contains(ApplicationConstants.SHARED_PREFERENCE_DISABLE_GYRO)) {
      preferences.edit().putBoolean(
          ApplicationConstants.SHARED_PREFERENCE_DISABLE_GYRO, !hasRotationSensor).apply();
    }

    // Do we at least have defaults for the main ones?
    int[] importantSensorTypes = {Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_LIGHT, Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_ORIENTATION};

    for (int sensorType : importantSensorTypes) {
      if (hasDefaultSensor(sensorType)) {
        Log.i(TAG, "No sensor of type " + sensorType);
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_TYPE + sensorType, "Sensor Absent", 0);
      } else {
        Log.i(TAG, "Sensor present of type " + sensorType);
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_TYPE + sensorType, "Sensor Present", 1);
      }
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
      analytics.trackEvent(
          Analytics.SENSOR_CATEGORY, Analytics.SENSOR_NAME, sensorType, 1);
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
      analytics.trackEvent(
          Analytics.SENSOR_CATEGORY, Analytics.SENSOR_LIAR, getSafeNameForSensor(sensor),
          1);
    }
    sensorManager.unregisterListener(dummy);
    return success;
  }
}
