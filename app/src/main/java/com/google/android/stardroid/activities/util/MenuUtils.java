package com.google.android.stardroid.activities.util;

import android.util.Log;
import android.view.Menu;

import java.lang.reflect.Method;

/**
 * Utility methods for working with Android Menus.
 */
public class MenuUtils {

  private static final String TAG = "MenuUtils";

  /**
   * Forces icons to be visible in the overflow menu.
   */
  public static void showOptionalIcons(Menu menu) {
    if (menu != null && menu.getClass().getSimpleName().equals("MenuBuilder")) {
      try {
        Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
        m.setAccessible(true);
        m.invoke(menu, true);
      } catch (Exception e) {
        Log.e(TAG, "Error forcing menu icons", e);
      }
    }
  }

  private MenuUtils() {}
}
