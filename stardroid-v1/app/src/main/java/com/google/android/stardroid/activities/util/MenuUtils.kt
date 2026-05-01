package com.google.android.stardroid.activities.util

import android.util.Log
import android.view.Menu
import java.lang.reflect.InvocationTargetException

/**
 * Utility methods for working with Android Menus.
 */
object MenuUtils {
    private const val TAG = "MenuUtils"

    /**
     * Forces icons to be visible in the overflow menu via reflection on MenuBuilder.
     * 
     * 
     * This relies on the internal `setOptionalIconsVisible` method of `MenuBuilder`,
     * which is not part of the public API and may change in future Android versions or on
     * non-standard OEM builds. There is currently no public API equivalent. Monitor
     * https://issuetracker.google.com for an official alternative.
     */
    @JvmStatic
    fun showOptionalIcons(menu: Menu?) {
        if (menu != null && menu.javaClass.getSimpleName() == "MenuBuilder") {
            try {
                val m = menu.javaClass.getDeclaredMethod("setOptionalIconsVisible", Boolean::class.javaPrimitiveType)
                m.isAccessible = true
                m.invoke(menu, true)
            } catch (e: ReflectiveOperationException) {
                Log.w(TAG, "Could not invoke setOptionalIconsVisible on menu", e)
            }
        }
    }
}
