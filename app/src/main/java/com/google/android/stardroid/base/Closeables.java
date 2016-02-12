// Copyright 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.base;

import java.io.Closeable;
import java.io.IOException;

import android.util.Log;

import com.google.android.stardroid.util.MiscUtil;

/**
 * Utility methods for working with {@link Closeable} objects.
 *
 * @author Brent Bryan
 */
public class Closeables {
  /**
   * Attempts to close the given object. All IOExceptions are caught and
   * silently ignored.
   */
  public static void closeSilently(@Nullable Closeable c) {
    if (c != null) {
      try {
        c.close();
      } catch (IOException e) {
        // Silently ignore exceptions.
      }
    }
  }

  /**
   * Attempts to close the given object. All IOExceptions are caught and logged.
   */
  public static void closeWithLog(@Nullable Closeable c) {
    if (c != null) {
      try {
        c.close();
      } catch (IOException e) {
        Log.e(MiscUtil.getTag(Closeable.class), e.getMessage());
      }
    }
  }

}
