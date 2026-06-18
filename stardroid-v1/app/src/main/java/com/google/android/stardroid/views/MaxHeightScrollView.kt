package com.google.android.stardroid.views

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * A [ScrollView] that grows to fit its content but never exceeds [maxHeightPx]. When the content
 * is shorter than the cap the view wraps it; when it is taller the view stops at the cap and the
 * content scrolls internally.
 *
 * Used by the object info card so very tall cards leave room around the dialog to tap-to-dismiss.
 * A non-positive [maxHeightPx] (the default) means "no cap".
 */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    /** Maximum height in pixels. Values <= 0 disable the cap. */
    var maxHeightPx: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val spec = if (maxHeightPx > 0) {
            MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, spec)
    }
}
