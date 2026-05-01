package com.google.android.stardroid.activities.util;

import android.view.View;
import android.widget.Toast;

/**
 * Utility class to provide Toast-based tooltips for views.
 * This is used as a fallback for standard tooltips on certain devices where native tooltips
 * are rendered unreadable due to OEM theme bugs.
 */
public class TooltipUtil {

    public enum Position {
        BELOW,
        RIGHT
    }

    /**
     * Sets up a long-click listener to show a tooltip using the view's content description.
     * The tooltip will appear below the view.
     */
    public static void setupToastTooltip(View view) {
        setupToastTooltip(view, Position.BELOW);
    }

    /**
     * Sets up a long-click listener to show a tooltip using the view's content description.
     */
    public static void setupToastTooltip(View view, Position position) {
        view.setOnLongClickListener(v -> {
            CharSequence desc = v.getContentDescription();
            return showToastTooltip(v, desc, position);
        });
    }

    /**
     * Sets up a long-click listener to show a tooltip using a custom description string.
     */
    public static void setupToastTooltip(View view, CharSequence desc, Position position) {
        view.setOnLongClickListener(v -> showToastTooltip(v, desc, position));
    }

    private static boolean showToastTooltip(View v, CharSequence desc, Position position) {
        if (desc != null && desc.length() > 0) {
            Toast toast = Toast.makeText(v.getContext(), desc, Toast.LENGTH_SHORT);
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            int xOffset = pos[0];
            int yOffset = pos[1];

            if (position == Position.RIGHT) {
                xOffset += v.getWidth();
            } else if (position == Position.BELOW) {
                yOffset += v.getHeight();
            }

            // Note that the gravity setting will be ignored on recent API levels (30+),
            // but it still works correctly on older devices.
            toast.setGravity(android.view.Gravity.TOP | android.view.Gravity.LEFT, xOffset, yOffset);
            toast.show();
            return true;
        }
        return false;
    }
}
