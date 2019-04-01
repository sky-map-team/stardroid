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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger;
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

/**
 * Edit the user's preferences.
 */
public class EditSettingsActivity extends PreferenceActivity {
  private MyPreferenceFragment preferenceFragment;
  
  public static class MyPreferenceFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preference_screen);
    }
  }
  /**
   * These must match the keys in the preference_screen.xml file.
   */
  private static final String LONGITUDE = "longitude";
  private static final String LATITUDE = "latitude";
  private static final String LOCATION = "location";
  private static final String TAG = MiscUtil.getTag(EditSettingsActivity.class);
  private Geocoder geocoder;
  private ActivityLightLevelManager activityLightLevelManager;
  @Inject Analytics analytics;
  @Inject SharedPreferences sharedPreferences;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ((StardroidApplication) getApplication()).getApplicationComponent().inject(this);
    activityLightLevelManager = new ActivityLightLevelManager(
        new ActivityLightLevelChanger(this, null),
        PreferenceManager.getDefaultSharedPreferences(this));
    geocoder = new Geocoder(this);
    preferenceFragment = new MyPreferenceFragment();
    getFragmentManager().beginTransaction().replace(android.R.id.content,
        preferenceFragment).commit();
    
  }

  @Override
  public void onStart() {
    super.onStart();
    analytics.trackPageView(Analytics.EDIT_SETTINGS_ACTIVITY);
    final Preference locationPreference = preferenceFragment.findPreference(LOCATION);
    Preference latitudePreference = preferenceFragment.findPreference(LATITUDE);
    Preference longitudePreference = preferenceFragment.findPreference(LONGITUDE);
    locationPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
    
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        Log.d(TAG, "Place to be updated to " + newValue);
        return setLatLongFromPlace(newValue.toString());
      }
    });
  
    latitudePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
    
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        ((EditTextPreference) locationPreference).setText("");
        return true;
      }
    });
  
    longitudePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
    
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        ((EditTextPreference) locationPreference).setText("");
        return true;
      }
    });

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

  protected boolean setLatLongFromPlace(String place) {
    List<Address> addresses;
    try {
      addresses = geocoder.getFromLocationName(place, 1);
    } catch (IOException e) {
      Toast.makeText(this, getString(R.string.location_unable_to_geocode), Toast.LENGTH_SHORT).show();
      return false;
    }
    if (addresses.size() == 0) {
      showNotFoundDialog(place);
      return false;
    }
    // TODO(johntaylor) let the user choose, but for now just pick the first.
    Address first = addresses.get(0);
    setLatLong(first.getLatitude(), first.getLongitude());
    return true;
  }

  private void setLatLong(double latitude, double longitude) {
    EditTextPreference latPreference = (EditTextPreference) preferenceFragment.findPreference(LATITUDE);
    EditTextPreference longPreference = (EditTextPreference) preferenceFragment.findPreference(LONGITUDE);
    latPreference.setText(Double.toString(latitude));
    longPreference.setText(Double.toString(longitude));
    String message = String.format(getString(R.string.location_place_found), latitude, longitude);
    Log.d(TAG, message);
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }

  private void showNotFoundDialog(String place) {
    String message = String.format(getString(R.string.location_not_found), place);
    AlertDialog.Builder dialog = new AlertDialog.Builder(this)
        .setTitle(R.string.location_not_found_title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, new OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
          }
        });
    dialog.show();
  }
}
