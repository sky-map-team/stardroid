package com.google.android.stardroid.activities.util

import android.view.Gravity
import android.view.View
import android.widget.Toast

/**
 * Utility class to provide Toast-based tooltips for views.
 * This is used as a fallback for standard tooltips on certain devices where native tooltips
 * are rendered unreadable due to OEM theme bugs.
 */
object TooltipUtil {

    /**
     * Sets up a long-click listener to show a tooltip using a custom description string.
     */
    @JvmStatic
    @JvmStatic
    @JvmOverloads
    fun setupToastTooltip(view: View, desc: CharSequence? = null, position: Position = Position.BELOW) {
        view.setOnLongClickListener { v ->
            val finalDesc = desc ?: v.contentDescription
            showToastTooltip(v, finalDesc, position)
        }
    }

    private fun showToastTooltip(v: View, desc: CharSequence?, position: Position?): Boolean {
        if (!desc.isNullOrEmpty()) {
            val toast = Toast.makeText(v.context, desc, Toast.LENGTH_SHORT)
            val pos = IntArray(2)
            v.getLocationOnScreen(pos)
            var xOffset = pos[0]
            var yOffset = pos[1]

            when (position) {
                Position.RIGHT -> xOffset += v.width
                Position.BELOW -> yOffset += v.height
                else -> android.util.Log.w("TooltipUtil", "Unknown position: $position")
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
