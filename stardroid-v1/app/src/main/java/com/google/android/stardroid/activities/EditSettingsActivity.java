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
package com.google.android.stardroid.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.View;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger;
import com.google.android.stardroid.activities.util.NightModeHelper;
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.activities.util.EdgeToEdgeFixer;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

/**
 * Edit the user's preferences.
 */
public class EditSettingsActivity extends PreferenceActivity
    implements ActivityLightLevelChanger.NightModeable {
  private MyPreferenceFragment preferenceFragment;

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  interface EditSettingsEntryPoint {
    Analytics analytics();
    SharedPreferences sharedPreferences();
  }

  public static class MyPreferenceFragment extends PreferenceFragment {
    private EditSettingsActivity parentActivity;

    public void setParentActivity(EditSettingsActivity activity) {
      this.parentActivity = activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preference_screen);
    }
  }

  private static final String TAG = MiscUtil.getTag(EditSettingsActivity.class);

  private boolean nightMode = false;
  private ActivityLightLevelManager activityLightLevelManager;
  private Analytics analytics;
  private SharedPreferences sharedPreferences;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    EditSettingsEntryPoint entryPoint = EntryPointAccessors.fromApplication(
        getApplicationContext(), EditSettingsEntryPoint.class);
    analytics = entryPoint.analytics();
    sharedPreferences = entryPoint.sharedPreferences();

    ActivityLightLevelChanger lightLevelChanger = new ActivityLightLevelChanger(
        getWindow(), sharedPreferences, this);
    activityLightLevelManager = new ActivityLightLevelManager(lightLevelChanger, sharedPreferences);

    preferenceFragment = new MyPreferenceFragment();
    preferenceFragment.setParentActivity(this);
    getFragmentManager().beginTransaction().replace(android.R.id.content,
        preferenceFragment).commit();

    // Apply edge-to-edge fix for Android 15+
    EdgeToEdgeFixer.applyEdgeToEdgeFixForActionBarActivity(this);
  }

  @Override
  public void onStart() {
    super.onStart();
    View rootView = findViewById(android.R.id.content);
    EdgeToEdgeFixer.applyTopPaddingForActionBar(this, rootView);

    Preference gyroPreference = preferenceFragment.findPreference(
        ApplicationConstants.SHARED_PREFERENCE_DISABLE_GYRO);
    gyroPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

      public boolean onPreferenceChange(Preference preference, Object newValue) {
        Log.d(TAG, "Toggling gyro preference " + newValue);
        enableNonGyroSensorPrefs(((Boolean) newValue));
        return true;
      }
    });

    enableNonGyroSensorPrefs(
        sharedPreferences.getBoolean(ApplicationConstants.SHARED_PREFERENCE_DISABLE_GYRO,
            false));
  }

  @Override
  public void onResume() {
    super.onResume();
    activityLightLevelManager.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    updatePreferences();
    activityLightLevelManager.onPause();
  }

  private void enableNonGyroSensorPrefs(boolean enabled) {
    // These settings aren't compatible with the gyro.
    preferenceFragment.findPreference(
        ApplicationConstants.SENSOR_SPEED_PREF_KEY).setEnabled(enabled);
    preferenceFragment.findPreference(
        ApplicationConstants.SENSOR_DAMPING_PREF_KEY).setEnabled(enabled);
    preferenceFragment.findPreference(
        ApplicationConstants.REVERSE_MAGNETIC_Z_PREFKEY).setEnabled(enabled);
  }

  /**
   * Updates preferences on singletons, so we don't have to register
   * preference change listeners for them.
   */
  private void updatePreferences() {
    Log.d(TAG, "Updating preferences");
    analytics.setEnabled(preferenceFragment.findPreference(Analytics.PREF_KEY).isEnabled());
  }

  @Override
  public void setNightMode(boolean nightMode) {
    this.nightMode = nightMode;
    applyNightMode();
  }

  private void applyNightMode() {
    // applyActionBarNightMode sets the background colour, title text colour, and logo tint —
    // the same three changes made by DynamicStarMapActivity and DiagnosticActivity.
    // No content-area tinting is attempted here because PreferenceFragment renders its own
    // rows using system-managed views that cannot be reliably recoloured without a full
    // custom preference renderer.
    NightModeHelper.applyActionBarNightMode(getActionBar(), this, nightMode);
  }
}
