package com.google.android.stardroid.activities.util;

import android.Manifest;
import android.app.Activity;
import androidx.fragment.app.FragmentManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import android.util.Log;

import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.activities.dialogs.LocationPermissionDeniedDialogFragment;
import com.google.android.stardroid.util.MiscUtil;

/**
 * Created by johntaylor on 4/2/16.
 */
public abstract class AbstractGooglePlayServicesChecker {
  protected static final String TAG = MiscUtil.getTag(GooglePlayServicesChecker.class);
  protected final Activity parent;
  protected final SharedPreferences preferences;
  private final LocationPermissionDeniedDialogFragment permissionDeniedDialog;
  private final FragmentManager fragmentManager;

  AbstractGooglePlayServicesChecker(Activity parent, SharedPreferences preferences,
                            LocationPermissionDeniedDialogFragment permissionDeniedDialog,
                            FragmentManager fragmentManager) {
    this.parent = parent;
    this.preferences = preferences;
    this.permissionDeniedDialog = permissionDeniedDialog;
    this.fragmentManager = fragmentManager;
  }

  /**
   * Checks whether play services is available and up to date and prompts the user
   * if necessary.
   * <p/>
   * Note that at present we only need it for location services so if the user is setting
   * their location manually we don't do the check.
   */
  public abstract void maybeCheckForGooglePlayServices();

  protected void checkLocationServicesEnabled() {
    if (ActivityCompat.checkSelfPermission(parent, Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "Location permission not enabled - requesting permission");
      // Request permission normally - if denied, runAfterPermissionsCheck will show options
      requestLocationPermission();
    } else {
      Log.d(TAG, "Location permission is granted");
    }
  }

  private void showLocationPermissionDialog() {
    permissionDeniedDialog.show(fragmentManager, "Location Permission Dialog");
  }

  private void requestLocationPermission() {
    ActivityCompat.requestPermissions(parent,
        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
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
      Log.i(TAG, "User denied permission - showing options dialog");
      showLocationPermissionDialog();
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
