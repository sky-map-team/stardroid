package com.google.android.stardroid.activities.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.Menu

/**
 * Utility methods for working with Android Menus.
 */
object MenuUtils {
    private const val TAG = "MenuUtils"
    private const val MENU_ICON_SIZE_DP = 24

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
        if (menu?.javaClass?.simpleName == "MenuBuilder") {
            try {
                val m = menu.javaClass.getDeclaredMethod("setOptionalIconsVisible", Boolean::class.javaPrimitiveType)
                m.isAccessible = true
                m.invoke(menu, true)
            } catch (e: ReflectiveOperationException) {
                Log.w(TAG, "Could not invoke setOptionalIconsVisible on menu", e)
            }
        }
    }

    /**
     * Forces every menu item's icon to report a consistent 24dp intrinsic size.
     *
     *
     * The overflow menu is rendered by the platform's own (non-AppCompat) `MenuBuilder`,
     * whose popup row layout varies across Android versions and OEM skins. Some
     * implementations size the icon `ImageView` from `wrap_content`, honouring whatever
     * intrinsic size the `Drawable` reports, while others constrain it to a fixed size
     * regardless. A raster icon that only ships a single density-bucket asset can resolve
     * to a slightly different intrinsic size depending on the device's exact screen
     * density and the platform's bitmap-scaling implementation, which is how an icon can
     * look correctly sized on one device/OS version but oversized on another even though
     * the underlying resource never changed. Wrapping every icon so it always reports
     * exactly 24dp removes that platform-version dependency entirely.
     */
    @JvmStatic
    fun normalizeIconSizes(menu: Menu?, context: Context) {
        if (menu == null) return
        val sizePx = (MENU_ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val icon = item.icon ?: continue
            if (icon is FixedSizeDrawable) continue
            if (icon.intrinsicWidth == sizePx && icon.intrinsicHeight == sizePx) continue
            item.icon = FixedSizeDrawable(icon.mutate(), sizePx)
        }
    }

    /** A [Drawable] wrapper that always reports [sizePx] as its intrinsic width/height. */
    private class FixedSizeDrawable(
        private val wrapped: Drawable,
        private val sizePx: Int
    ) : Drawable() {
        init {
            wrapped.setBounds(0, 0, sizePx, sizePx)
        }

        override fun draw(canvas: Canvas) {
            wrapped.bounds = bounds
            wrapped.draw(canvas)
        }

        override fun setAlpha(alpha: Int) {
            wrapped.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            wrapped.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = wrapped.opacity

        override fun getIntrinsicWidth(): Int = sizePx

        override fun getIntrinsicHeight(): Int = sizePx
    }
}
