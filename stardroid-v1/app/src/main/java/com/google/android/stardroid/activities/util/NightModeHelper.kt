package com.google.android.stardroid.activities.util

import android.app.ActionBar
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.stardroid.R

/**
 * Utility methods for applying night mode styling to Android UI elements.
 * 
 * 
 * All colour values come from `res/values/colors.xml`. Activities should call
 * [applyActionBarNightMode] from their `setNightMode()` implementation; dialogs
 * should call [applyAlertDialogNightMode] from inside a
 * `setOnShowListener` callback.
 */
object NightModeHelper {
    /**
     * Applies night mode styling to an ActionBar: background colour, title text colour, and
     * Sky Map logo tint.
     */
    @JvmStatic
    fun applyActionBarNightMode(actionBar: ActionBar?, activity: Activity, nightMode: Boolean) {
        if (actionBar == null) return
        actionBar.setBackgroundDrawable(
            ColorDrawable(activity.getColor(if (nightMode) R.color.night_red_overlay else R.color.day_overlay))
        )
        val textColor =
            activity.getColor(if (nightMode) R.color.night_text_color else android.R.color.white)
        val title = activity.getTitle()
        if (title != null) {
            val styledTitle = SpannableString(title.toString())
            styledTitle.setSpan(ForegroundColorSpan(textColor), 0, styledTitle.length, 0)
            actionBar.setTitle(styledTitle)
        }
        // Tint the Sky Map logo shown in the action bar
        var icon = activity.getResources().getDrawable(
            R.mipmap.skymap_logo_new, activity.getTheme()
        )
        if (icon != null) {
            icon = icon.mutate()
            if (nightMode) {
                icon.setColorFilter(textColor, PorterDuff.Mode.MULTIPLY)
            } else {
                icon.clearColorFilter()
            }
            actionBar.setIcon(icon)
        }
    }

    /**
     * Recursively sets the text and link colour on every [TextView] (including
     * [Button]s) in a view hierarchy. Equivalent to calling
     * [tintTextViews] with the same colour for both.
     */
    @JvmStatic
    @JvmOverloads
    fun tintTextViews(root: ViewGroup, textColor: Int, linkColor: Int = textColor) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is TextView) {
                child.setTextColor(textColor)
                child.setLinkTextColor(linkColor)
            } else if (child is ViewGroup) {
                tintTextViews(child, textColor, linkColor)
            }
        }
    }

    /**
     * Toggles the `night-mode` CSS class on a [WebView]'s `<body>` element.
     * Requires JavaScript to be enabled on the WebView. Safe to call if `webView` is null.
     */
    @JvmStatic
    fun applyWebViewNightMode(webView: WebView?, nightMode: Boolean) {
        if (webView == null) return
        val js = if (nightMode)
            "document.body.classList.add('night-mode')"
        else
            "document.body.classList.remove('night-mode')"
        webView.evaluateJavascript(js, null)
    }

    /**
     * Applies a night mode colour filter to all icons in an options [Menu].
     * Uses [PorterDuff.Mode.MULTIPLY] so icon detail is preserved.
     */
    @JvmStatic
    fun tintMenuIcons(menu: Menu, nightMode: Boolean, context: Context) {
        val color = if (nightMode) context.getColor(R.color.night_text_color) else Color.WHITE
        for (i in 0..<menu.size()) {
            val item = menu.getItem(i)
            val icon = item.getIcon()
            if (icon != null) {
                icon.mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY)
            }

            // Also tint custom action views (like our search/dim/time buttons)
            val actionView = item.actionView
            if (actionView is ImageButton) {
                if (nightMode) {
                    actionView.setColorFilter(color, PorterDuff.Mode.MULTIPLY)
                } else {
                    actionView.clearColorFilter()
                }
            } else if (actionView is ViewGroup) {
                val btn = actionView.findViewById<ImageButton>(R.id.action_icon)
                if (btn != null) {
                    if (nightMode) {
                        btn.setColorFilter(color, PorterDuff.Mode.MULTIPLY)
                    } else {
                        btn.clearColorFilter()
                    }
                }
            }

            val titleText = item.title
            if (titleText != null) {
                val title = SpannableString(titleText)
                title.setSpan(
                    ForegroundColorSpan(color), 0, title.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
                item.setTitle(title)
            }
        }
    }

    /**
     * Applies night mode tinting to the chrome of a shown [AlertDialog]: title text,
     * title divider, and button text.
     * 
     * 
     * Must be called from within a [android.content.DialogInterface.OnShowListener]
     * callback because dialog buttons are not accessible until the dialog is shown.
     */
    @JvmStatic
    fun applyAlertDialogNightMode(dialog: AlertDialog, isNight: Boolean) {
        if (!isNight) return
        
        val color = dialog.getContext().getColor(R.color.night_text_color)
        val titleId =
            dialog.getContext().getResources().getIdentifier("alertTitle", "id", "android")
        if (titleId != 0) {
            val titleView = dialog.findViewById<TextView?>(titleId)
            if (titleView != null) titleView.setTextColor(color)
        }
        val dividerId = dialog.getContext().getResources()
            .getIdentifier("titleDivider", "id", "android")
        if (dividerId != 0) {
            val divider = dialog.findViewById<View?>(dividerId)
            if (divider != null) {
                divider.setBackgroundColor(
                    dialog.getContext().getColor(R.color.night_text_color)
                )
            }
        }
        val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        if (posBtn != null) posBtn.setTextColor(color)
        val negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        if (negBtn != null) negBtn.setTextColor(color)
        val neuBtn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        if (neuBtn != null) neuBtn.setTextColor(color)
    }
}
