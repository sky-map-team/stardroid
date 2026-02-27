package com.google.android.stardroid.activities.util;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.stardroid.R;

/**
 * Utility methods for applying night mode styling to Android UI elements.
 *
 * <p>All colour values come from {@code res/values/colors.xml}. Activities should call
 * {@link #applyActionBarNightMode} from their {@code setNightMode()} implementation; dialogs
 * should call {@link #applyAlertDialogNightMode} from inside a
 * {@code setOnShowListener} callback.
 */
public class NightModeHelper {

  /**
   * Applies night mode styling to an ActionBar: background colour, title text colour, and
   * Sky Map logo tint.
   */
  public static void applyActionBarNightMode(ActionBar actionBar, Activity activity, boolean nightMode) {
    if (actionBar == null) return;
    actionBar.setBackgroundDrawable(
        new ColorDrawable(activity.getColor(nightMode ? R.color.night_red_overlay : R.color.day_overlay)));
    int textColor = activity.getColor(nightMode ? R.color.night_text_color : android.R.color.white);
    CharSequence title = activity.getTitle();
    if (title != null) {
      SpannableString styledTitle = new SpannableString(title.toString());
      styledTitle.setSpan(new ForegroundColorSpan(textColor), 0, styledTitle.length(), 0);
      actionBar.setTitle(styledTitle);
    }
    // Tint the Sky Map logo shown in the action bar
    Drawable icon = activity.getResources().getDrawable(R.drawable.skymap_logo, activity.getTheme());
    if (icon != null) {
      icon = icon.mutate();
      if (nightMode) {
        icon.setColorFilter(textColor, PorterDuff.Mode.MULTIPLY);
      } else {
        icon.clearColorFilter();
      }
      actionBar.setIcon(icon);
    }
  }

  /**
   * Recursively sets the text colour on every {@link TextView} (including {@link Button}s)
   * in a view hierarchy.
   */
  public static void tintTextViews(ViewGroup root, int color) {
    for (int i = 0; i < root.getChildCount(); i++) {
      View child = root.getChildAt(i);
      if (child instanceof TextView) {
        ((TextView) child).setTextColor(color);
      } else if (child instanceof ViewGroup) {
        tintTextViews((ViewGroup) child, color);
      }
    }
  }

  /**
   * Applies night mode tinting to the chrome of a shown {@link AlertDialog}: title text,
   * title divider, and button text.
   *
   * <p>Must be called from within a {@link android.content.DialogInterface.OnShowListener}
   * callback because dialog buttons are not accessible until the dialog is shown.
   */
  public static void applyAlertDialogNightMode(AlertDialog dialog, boolean isNight) {
    int color = isNight ? dialog.getContext().getColor(R.color.night_text_color) : Color.WHITE;
    int titleId = dialog.getContext().getResources().getIdentifier("alertTitle", "id", "android");
    if (titleId != 0) {
      TextView titleView = dialog.findViewById(titleId);
      if (titleView != null) titleView.setTextColor(color);
    }
    if (isNight) {
      int dividerId = dialog.getContext().getResources()
          .getIdentifier("titleDivider", "id", "android");
      if (dividerId != 0) {
        View divider = dialog.findViewById(dividerId);
        if (divider != null) {
          divider.setBackgroundColor(dialog.getContext().getColor(R.color.night_text_color));
        }
      }
    }
    Button posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
    if (posBtn != null) posBtn.setTextColor(color);
    Button negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    if (negBtn != null) negBtn.setTextColor(color);
  }

  private NightModeHelper() {}
}
