package com.google.android.stardroid.activities.util;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.activities.dialogs.LocationPermissionRationaleFragment;
import com.google.android.stardroid.control.LocationController;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * Created by johntaylor on 4/2/16.
 */
public class GooglePlayServicesChecker implements LocationPermissionRationaleFragment.Callback {
  private static final String TAG = MiscUtil.getTag(GooglePlayServicesChecker.class);
  private final Activity parent;
  private final SharedPreferences preferences;
  private final GoogleApiAvailability apiAvailability;
  private final LocationPermissionRationaleFragment rationaleDialog;
  private final FragmentManager fragmentManager;

  @Inject
  GooglePlayServicesChecker(Activity parent, SharedPreferences preferences,
                            GoogleApiAvailability apiAvailability,
                            LocationPermissionRationaleFragment rationaleDialog,
                            FragmentManager fragmentManager) {
    this.parent = parent;
    this.preferences = preferences;
    this.apiAvailability = apiAvailability;
    this.rationaleDialog = rationaleDialog;
    this.fragmentManager = fragmentManager;
    rationaleDialog.setCallback(this);
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
        Toast.makeText(parent, R.string.play_services_error, Toast.LENGTH_LONG).show();
      }
    }
    checkLocationServicesEnabled();
  }

  private void checkLocationServicesEnabled() {
    if (ActivityCompat.checkSelfPermission(parent, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "Location permission not enabled - maybe prompting user");
      // Check Permissions now
      if (ActivityCompat.shouldShowRequestPermissionRationale(
          parent, Manifest.permission.ACCESS_FINE_LOCATION)) {
        rationaleDialog.show(fragmentManager, "Rationale Dialog");
      } else {
        requestLocationPermission();
      }
    } else {
      Log.d(TAG, "Location permission is granted");
    }
  }

  private void requestLocationPermission() {
    ActivityCompat.requestPermissions(parent,
        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
        DynamicStarMapActivity.GOOGLE_PLAY_SERVICES_REQUEST_LOCATION_PERMISSION_CODE);
  }

  /**
   * Called after a request to check permissions.
   */
  public void runAfterPermissionsCheck(int requestCode,
                                       String[] permissions,
                                       int[] grantResults) {
    if (grantResults.length == 1
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      Log.i(TAG, "User granted permission");
    } else {
      Log.i(TAG, "User denied permission");
      // TODO(jontayler): Send them to the location dialog;
    }
  }

  /**
   * Called after the user is prompted to resolve any issues.
   */
  public void runAfterDialog() {
    // Just log for now.
    Log.d(TAG, "Play Services Dialog has been shown");
  }

  public void done() {
    Log.d(TAG, "Location rationale Dialog has been shown");
    requestLocationPermission();
  }
}
