// Copyright 2010 Google Inc.
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

package com.google.android.stardroid.util;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utilities for getting the version number of Android we're running under
 * and running methods that only exist in later versions.  The point of
 * keeping them altogether is so that they're easier to find once we
 * stop supporting lower versions.  This class should be reviewed periodically
 * and any methods that are no longer required inlined.
 *
 * @author John Taylor
 */
public class OsVersions {
  private static final String TAG = MiscUtil.getTag(OsVersions.class);
  private static final int VIEW_STATUS_BAR_HIDDEN = 1;
  private static final int VIEW_STATUS_BAR_VISIBLE = 0;

  private OsVersions() {}
  /**
   * Returns the android release code number.  e.g. CUPCAKE = 3, FROYO = 8.
   */
  public static int version() {
    // TODO(johntaylor): this method can simply return android.os.Build.VERSION.SDK_INT
    // once we no longer support 1.5.
    String versionString = android.os.Build.VERSION.SDK;
    return Integer.parseInt(versionString);
  }

  /**
   * Invokes Activity.overridePendingTransition if possible.
   *
   * Eclair (v5) and above.
   */
  public static void overridePendingTransition(Activity caller, int in, int out) {
    invokeByReflection(caller, new Class<?>[] {int.class, int.class},
                       new Object[] {in, out}, "overridePendingTransition");
  }

  // MotionEvent methods
  /**
   * Invokes MotionEvent.getPointerCount if possible.
   *
   * Eclair (v5) and above.
   */
  public static int getPointerCount(MotionEvent caller) {
    try {
      return (Integer) invokeByReflection(
          caller, new Class<?>[0], new Object[0], "getPointerCount");
    } catch (UnsupportedOperationException e) {
      // Obviously if we're pre Eclair, there can only be one pointer.
      return 1;
    }
  }

  /**
   * Invokes MotionEvent.getX(int) if possible.
   *
   * Eclair (v5) and above.
   */
  public static float getX(MotionEvent caller, int index) {
    try {
      return (Float) invokeByReflection(
          caller, new Class<?>[]{int.class}, new Object[]{index}, "getX");
    } catch (UnsupportedOperationException e) {
      // Obviously if we're pre-Eclair, there can only be one pointer.
      return caller.getX();
    }
  }

  /**
   * Invokes MotionEvent.getY(int) if possible.
   *
   * Eclair (v5) and above.
   */
  public static float getY(MotionEvent caller, int index) {
    try {
      return (Float) invokeByReflection(
          caller, new Class<?>[]{int.class}, new Object[]{index}, "getY");
    } catch (UnsupportedOperationException e) {
      // Obviously if we're pre-Eclair, there can only be one pointer.
      return caller.getY();
    }
  }

  /**
   * Sets button backlight brightness.  Only works from Eclair (8) onwards.
   */
  public static void setButtonBrightness(float buttonBrightness,
      WindowManager.LayoutParams params) {
    try {
      Log.d(TAG, "Setting button brightness");
      setByReflection(params, "buttonBrightness", buttonBrightness);
    } catch (UnsupportedOperationException e) {
      Log.e(TAG, "Unable to change button brightness");
    }
  }

  /**
   * Returns the value of Windows.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
   * in Eclair and above.  Otherwise returns -1.0, which seems to
   * give equivalent behavior on Cupcake devices.
   */
  public static float brightnessOverrideNoneValue() {
    try {
      return (Float) getByReflection(WindowManager.LayoutParams.class, "BRIGHTNESS_OVERRIDE_NONE");
    } catch (UnsupportedOperationException e) {
      return -1.0f;
    }
  }

  /**
   * Returns the value of Windows.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
   * in Eclair and above.  Otherwise returns 0.0, which is equivalent
   * at present.
   */
  public static float brightnessOverrideOffValue() {
    try {
      return (Float) getByReflection(WindowManager.LayoutParams.class, "BRIGHTNESS_OVERRIDE_OFF");
    } catch (UnsupportedOperationException e) {
      return 0.0f;
    }
  }

  /**
   * Request that the system status bar be made visible, or hidden.  Honeycomb and above only.
   */
  public static void setSystemStatusBarVisible(View view, boolean visible) {
    try {
      int status = visible ? VIEW_STATUS_BAR_VISIBLE : VIEW_STATUS_BAR_HIDDEN;
      invokeByReflection(view, new Class[] {int.class}, new Object[] {status},
                         "setSystemUiVisibility");
    } catch (UnsupportedOperationException e) {
      // Doesn't matter.
    }
  }

  private static Object invokeByReflection(Object caller, Class<?>[] classes,
      Object[] args, String methodName) {
    try {
      Method m = caller.getClass().getMethod(methodName, classes);
      return m.invoke(caller, args);
    } catch (IllegalAccessException e) {
      Log.e(TAG, "Failed to invoke method", e);
      throw new UnsupportedOperationException(methodName + " not supported");
    } catch (InvocationTargetException e) {
      Log.e(TAG, "Failed to invoke method", e);
      throw new UnsupportedOperationException(methodName + " not supported");
    } catch (NoSuchMethodException e) {
      Log.e(TAG, "Failed to invoke method", e);
      throw new UnsupportedOperationException(methodName + " not supported");
    }
  }

  private static void setByReflection(Object object, String fieldName, Object value) {
    try {
      Field f = object.getClass().getField(fieldName);
      f.set(object, value);
    } catch (IllegalAccessException e) {
      Log.e(TAG, "Failed to get field", e);
      throw new UnsupportedOperationException(fieldName + " not supported");
    } catch (SecurityException e) {
      Log.e(TAG, "Failed to get field", e);
      throw new UnsupportedOperationException(fieldName + " not supported");
    } catch (NoSuchFieldException e) {
      Log.e(TAG, "Failed to get field", e);
      throw new UnsupportedOperationException(fieldName + " not supported");
    }
  }

  private static Object getByReflection(Object object, String fieldName) {
    try {
      Field f = object.getClass().getField(fieldName);
      return f.get(object);
    } catch (IllegalAccessException e) {
      Log.e(TAG, "Failed to get field", e);
      throw new UnsupportedOperationException(fieldName + " not supported");
    } catch (SecurityException e) {
      Log.e(TAG, "Failed to get field", e);
      throw new UnsupportedOperationException(fieldName + " not supported");
    } catch (NoSuchFieldException e) {
      Log.e(TAG, "Failed to get field", e);
      throw new UnsupportedOperationException(fieldName + " not supported");
    }
  }

  private static Object getByReflection(Class<?> clazz, String staticFieldName) {
    try {
      Field f = clazz.getField(staticFieldName);
      return f.get(null);
    } catch (IllegalAccessException e) {
      Log.e(TAG, "Failed to get field", e);
      throw new UnsupportedOperationException(staticFieldName + " not supported");
    } catch (SecurityException e) {
      Log.e(TAG, "Failed to get field", e);
      throw new UnsupportedOperationException(staticFieldName + " not supported");
    } catch (NoSuchFieldException e) {
      Log.e(TAG, "Failed to get field", e);
      throw new UnsupportedOperationException(staticFieldName + " not supported");
    }
  }
}
