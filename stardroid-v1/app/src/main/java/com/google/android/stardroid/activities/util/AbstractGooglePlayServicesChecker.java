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
    boolean hasFine = ActivityCompat.checkSelfPermission(parent,
        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    boolean hasCoarse = ActivityCompat.checkSelfPermission(parent,
        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    if (!hasFine && !hasCoarse) {
      Log.d(TAG, "Location permission not enabled - requesting permission");
      requestLocationPermission();
    } else {
      Log.d(TAG, "Location permission is granted (fine=" + hasFine + " coarse=" + hasCoarse + ")");
    }
  }

  private void showLocationPermissionDialog() {
    permissionDeniedDialog.show(fragmentManager, "Location Permission Dialog");
  }

  private void requestLocationPermission() {
    // Request both fine and coarse so Android 12+ shows the precise/approximate chooser.
    // The fused location provider requires ACCESS_FINE_LOCATION to reliably deliver
    // getCurrentLocation() callbacks on devices without GPS (e.g. Chrome OS).
    ActivityCompat.requestPermissions(parent,
        new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION},
        DynamicStarMapActivity.GOOGLE_PLAY_SERVICES_REQUEST_LOCATION_PERMISSION_CODE);
  }

  /**
   * Called after a request to check permissions.
   */
  public void runAfterPermissionsCheck(int requestCode,
                                       String[] permissions,
                                       int[] grantResults) {
    // Any granted result (fine or coarse) counts as success.
    boolean anyGranted = false;
    for (int result : grantResults) {
      if (result == PackageManager.PERMISSION_GRANTED) {
        anyGranted = true;
        break;
      }
    }
    if (anyGranted) {
      Log.i(TAG, "User granted location permission");
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
