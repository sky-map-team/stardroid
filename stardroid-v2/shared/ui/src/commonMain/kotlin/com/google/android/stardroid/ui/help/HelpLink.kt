/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.help

import com.google.android.stardroid.ui.common.APP_LINK_SCHEME

/**
 * Where a `skymap://` link in the help document goes. The document used to be a dead end — it
 * named a feature and left the reader to go find it — so each of these turns a sentence into
 * the thing it describes.
 */
sealed interface HelpLink {
    /**
     * The widget catalogue. Help raises its own copy of the sheet rather than sending the
     * reader to the map's: the sheet is a transient overlay, not a destination, and adding a
     * widget hands off to the system's pin dialog, so there is nothing to come back from.
     */
    data object Widgets : HelpLink

    /** Another part of this same document, scrolled to in place. */
    data class Anchor(
        val section: String,
    ) : HelpLink

    /** A screen elsewhere in the app, which only the navigation host can reach. */
    enum class Destination : HelpLink {
        SETTINGS,
        DIAGNOSTICS,
        CALIBRATE,
        GALLERY,
        TUTORIAL,

        /** The system's per-app settings page, where a denied permission is re-granted. */
        APP_SETTINGS,
    }
}

private const val ANCHOR_HOST = "help"

/**
 * Parses a `skymap://` href, or returns null for anything unrecognised.
 *
 * Null rather than an exception on purpose: these hrefs live in translated string resources,
 * so a locale that arrives with a mangled or invented target must leave the link inert, not
 * take the help screen down. `HelpLinksTest` keeps the resources themselves honest.
 */
internal fun parseHelpLink(url: String): HelpLink? {
    if (!url.startsWith(APP_LINK_SCHEME)) return null
    val target = url.removePrefix(APP_LINK_SCHEME)
    if (target.startsWith("$ANCHOR_HOST#")) {
        val anchor = target.removePrefix("$ANCHOR_HOST#")
        return if (anchor in HELP_ANCHORS) HelpLink.Anchor(anchor) else null
    }
    return when (target) {
        "widgets" -> HelpLink.Widgets
        "settings" -> HelpLink.Destination.SETTINGS
        "diagnostics" -> HelpLink.Destination.DIAGNOSTICS
        "calibrate" -> HelpLink.Destination.CALIBRATE
        "gallery" -> HelpLink.Destination.GALLERY
        "tutorial" -> HelpLink.Destination.TUTORIAL
        "app-settings" -> HelpLink.Destination.APP_SETTINGS
        else -> null
    }
}
