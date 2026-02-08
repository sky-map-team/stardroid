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
package com.google.android.stardroid.activities.util;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;

import com.google.android.stardroid.R;

/**
 * Utility to fix Android 15+ edge-to-edge display issues for activities with action bars.
 *
 * On Android 15 (API 35+), the system enforces edge-to-edge display by default, which causes
 * content to draw behind the status bar and action bar. This class provides methods to:
 * 1. Opt the activity out of system window insets (API 30+)
 * 2. Calculate and apply appropriate padding to account for status bar and action bar (API 35+)
 *
 * @author John Taylor
 */
public class EdgeToEdgeFixer {

  /**
   * Apply edge-to-edge fix for an activity with an action bar.
   * Call this in onCreate() after setContentView().
   *
   * @param activity The activity to fix
   */
  public static void applyEdgeToEdgeFixForActionBarActivity(Activity activity) {
    // Step 1: Ensure window fits system windows for proper layout (API 30+)
    if (Build.VERSION.SDK_INT >= 30) {
      activity.getWindow().setDecorFitsSystemWindows(true);
    }
  }

  /**
   * Apply top and bottom padding to a view to account for status bar, action bar, and
   * navigation bar. Call this in onStart() to ensure the view hierarchy is fully initialized.
   *
   * @param activity The activity context
   * @param contentView The root content view to apply padding to
   */
  public static void applyTopPaddingForActionBar(Activity activity, View contentView) {
    // Only needed for Android 15+ where edge-to-edge is enforced
    if (Build.VERSION.SDK_INT < 35) {
      return;
    }

    if (contentView == null) {
      return;
    }

    // Save original padding on first call to ensure idempotency and preserve any pre-existing padding
    Rect originalPadding = (Rect) contentView.getTag(R.id.original_padding_tag);
    if (originalPadding == null) {
      originalPadding = new Rect(
          contentView.getPaddingLeft(),
          contentView.getPaddingTop(),
          contentView.getPaddingRight(),
          contentView.getPaddingBottom());
      contentView.setTag(R.id.original_padding_tag, originalPadding);
    }

    // Get action bar height
    TypedValue tv = new TypedValue();
    int actionBarHeight = 0;
    if (activity.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
      actionBarHeight = TypedValue.complexToDimensionPixelSize(
          tv.data, activity.getResources().getDisplayMetrics());
    }

    // Get status bar height
    int statusBarHeight = 0;
    int statusBarResourceId = activity.getResources().getIdentifier(
        "status_bar_height", "dimen", "android");
    if (statusBarResourceId > 0) {
      statusBarHeight = activity.getResources().getDimensionPixelSize(statusBarResourceId);
    }

    // Get navigation bar height
    int navigationBarHeight = 0;
    int navBarResourceId = activity.getResources().getIdentifier(
        "navigation_bar_height", "dimen", "android");
    if (navBarResourceId > 0) {
      navigationBarHeight = activity.getResources().getDimensionPixelSize(navBarResourceId);
    }

    // Add system bar heights to original padding (preserves any pre-existing padding)
    contentView.setPadding(
        originalPadding.left,
        originalPadding.top + actionBarHeight + statusBarHeight,
        originalPadding.right,
        originalPadding.bottom + navigationBarHeight);

    // For scrollable content (like ListView in PreferenceFragment), disable clip to padding
    // so content scrolls under the padding area
    if (contentView instanceof android.view.ViewGroup) {
      ((android.view.ViewGroup) contentView).setClipToPadding(false);
    }
  }
}
