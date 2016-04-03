package com.google.android.stardroid.activities.util;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.control.LocationController;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * Created by johntaylor on 4/2/16.
 */
public class GooglePlayServicesChecker {
  private static final String TAG = MiscUtil.getTag(GooglePlayServicesChecker.class);
  private final Activity parent;
  private final SharedPreferences preferences;
  //private final GoogleApiClient apiClient;
  private final GoogleApiAvailability apiAvailability;

  @Inject
  GooglePlayServicesChecker(Activity parent, SharedPreferences preferences,
                            /*GoogleApiClient apiClient, */GoogleApiAvailability apiAvailability) {
    this.parent = parent;
    this.preferences = preferences;
    //this.apiClient = apiClient;
    this.apiAvailability = apiAvailability;
  }

  /**
   * Checks whether play services is available and up to date and prompts the user
   * if necessary.
   * <p/>
   * Note that at present we only need it for location services so if the user is setting
   * their location manually we don't do the check.
   */
  public void maybeCheckForGooglePlayServices() {
    Log.d(TAG, "Google Play Services check");
    if (preferences.getBoolean(LocationController.NO_AUTO_LOCATE, false)) {
      Log.d(TAG, "Auto location disabled - not checking for GMS");
      return;
    }
    int googlePlayServicesAvailability = apiAvailability.isGooglePlayServicesAvailable(parent);
    if (googlePlayServicesAvailability == ConnectionResult.SUCCESS) {
      Log.d(TAG, "Google Play Services is available and up to date");
    } else {
      Log.d(TAG, "Google Play Status availability: " + googlePlayServicesAvailability);
      if (apiAvailability.isUserResolvableError(googlePlayServicesAvailability)) {
        Log.d(TAG, "...but we can fix it");
        apiAvailability.getErrorDialog(parent, googlePlayServicesAvailability,
            DynamicStarMapActivity.GOOGLE_PLAY_SERVICES_REQUEST_CODE).show();
      } else {
        Log.d(TAG, "...and we can't fix it");
        // For now just warn the user, though we may need to do something like disable
        // auto location.
        Toast.makeText(parent, "Warning: Google Play Services error - Automatic location might not work", Toast.LENGTH_LONG);
      }
    }
    checkLocationServicesEnabled();
  }

  private void checkLocationServicesEnabled() {
    if (ActivityCompat.checkSelfPermission(parent, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "Location permission not enabled - prompting user");
      // Check Permissions Now
      ActivityCompat.requestPermissions(parent,
          new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
          DynamicStarMapActivity.GOOGLE_PLAY_SERVICES_REQUEST_LOCATION_PERMISSION_CODE);
    } else {
      Log.d(TAG, "Location permission is granted");
    }
  }

  /**
   * Called after a request to check permissions.
   */
  public void runAfterPermissionsCheck(int requestCode,
                                       String[] permissions,
                                       int[] grantResults) {
    if(grantResults.length == 1
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      Log.i(TAG, "User granted permission");
    } else {
      Log.i(TAG, "User denied permission");
      // Send them to the location dialog;
    }
  }

  /**
   * Called after the user is prompted to resolve any issues.
   */
  public void runAfterDialog() {
    // Just log for now.
    Log.d(TAG, "Play Services Dialog has been shown");
  }
}
