package com.google.android.stardroid.activities.util;

import android.util.Log;
import android.view.Menu;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility methods for working with Android Menus.
 */
public class MenuUtils {

  private static final String TAG = "MenuUtils";

  /**
   * Forces icons to be visible in the overflow menu via reflection on MenuBuilder.
   *
   * <p>This relies on the internal {@code setOptionalIconsVisible} method of {@code MenuBuilder},
   * which is not part of the public API and may change in future Android versions or on
   * non-standard OEM builds. There is currently no public API equivalent. Monitor
   * https://issuetracker.google.com for an official alternative.
   */
  public static void showOptionalIcons(Menu menu) {
    if (menu != null && menu.getClass().getSimpleName().equals("MenuBuilder")) {
      try {
        Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
        m.setAccessible(true);
        m.invoke(menu, true);
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        Log.w(TAG, "Could not invoke setOptionalIconsVisible on menu", e);
      }
    }
  }

  private MenuUtils() {}
}
