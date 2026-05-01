package com.google.android.stardroid.activities.util

import android.view.Gravity
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.Toast

/**
 * Utility class to provide Toast-based tooltips for views.
 * This is used as a fallback for standard tooltips on certain devices where native tooltips
 * are rendered unreadable due to OEM theme bugs.
 */
object TooltipUtil {
    /**
     * Sets up a long-click listener to show a tooltip using the view's content description.
     */
    /**
     * Sets up a long-click listener to show a tooltip using the view's content description.
     * The tooltip will appear below the view.
     */
    @JvmOverloads
    fun setupToastTooltip(view: View, position: Position? = Position.BELOW) {
        view.setOnLongClickListener(OnLongClickListener { v: View? ->
            val desc = v!!.getContentDescription()
            TooltipUtil.showToastTooltip(v, desc, position)
        })
    }

    /**
     * Sets up a long-click listener to show a tooltip using a custom description string.
     */
    @JvmStatic
    fun setupToastTooltip(view: View, desc: CharSequence?, position: Position?) {
        view.setOnLongClickListener(OnLongClickListener { v: View? ->
            TooltipUtil.showToastTooltip(
                v!!,
                desc,
                position
            )
        })
    }

    private fun showToastTooltip(v: View, desc: CharSequence?, position: Position?): Boolean {
        if (desc != null && desc.length > 0) {
            val toast = Toast.makeText(v.getContext(), desc, Toast.LENGTH_SHORT)
            val pos = IntArray(2)
            v.getLocationOnScreen(pos)
            var xOffset = pos[0]
            var yOffset = pos[1]

            if (position == Position.RIGHT) {
                xOffset += v.getWidth()
            } else if (position == Position.BELOW) {
                yOffset += v.getHeight()
            }

            // Note that the gravity setting will be ignored on recent API levels (30+),
            // but it still works correctly on older devices.
            toast.setGravity(Gravity.TOP or Gravity.LEFT, xOffset, yOffset)
            toast.show()
            return true
        }
        return false
    }

    enum class Position {
        BELOW,
        RIGHT
    }
}
