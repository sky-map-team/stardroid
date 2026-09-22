/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.help

import androidx.annotation.StringRes
import com.google.android.stardroid.R

/**
 * One entry of the help document, in render order.
 *
 * The document used to be three concatenated blobs split around the native pieces that
 * interleave with it. It is a list of sections instead so that Help's search box can filter it
 * and `skymap://help#<anchor>` links can scroll to a named part of it — neither of which a
 * single 11 KB string can offer.
 */
internal sealed interface HelpItem {
    /** Stable id for `skymap://help#<anchor>` links and for the lazy list's item key. */
    val anchor: String

    /**
     * One `<h2>` section of the document, rendered from its string resource (D78: the split
     * keeps a copy edit to one section from retranslating the whole document in every locale).
     */
    data class Prose(
        override val anchor: String,
        @StringRes val html: Int,
        /**
         * An `<h1>` divider carrying no prose of its own. Hidden while a search is active: a
         * bare "Miscellaneous and Troubleshooting" heading above a filtered list titles
         * nothing.
         */
        val divider: Boolean = false,
    ) : HelpItem

    /**
     * The deep-sky symbol legend, which is drawn natively from the catalog icons rather than
     * written as HTML, so it has no string of its own. It carries its own heading.
     */
    data object SymbolKey : HelpItem {
        override val anchor = "symbols"
    }
}

/**
 * The help document. Adding a section means adding its key to `help.xml` *and* an entry here;
 * the anchor is a permanent name, since translated copy may link to it.
 */
internal val HELP_DOCUMENT =
    listOf(
        HelpItem.Prose("home", R.string.help_home_link),
        HelpItem.Prose("intro", R.string.help_intro),
        HelpItem.Prose("navigating", R.string.help_navigating),
        HelpItem.SymbolKey,
        HelpItem.Prose("layers", R.string.help_layers),
        HelpItem.Prose("info_cards", R.string.help_info_cards),
        HelpItem.Prose("search", R.string.help_search),
        HelpItem.Prose("time_travel", R.string.help_time_travel),
        HelpItem.Prose("night_vision", R.string.help_night_vision),
        HelpItem.Prose("other", R.string.help_other, divider = true),
        HelpItem.Prose("gallery", R.string.help_gallery),
        HelpItem.Prose("widgets", R.string.help_widgets),
        HelpItem.Prose("location", R.string.help_location),
        // Lives in eula.xml, not help.xml: the terms screen renders the same key, so the
        // permission disclosure is written and translated exactly once (see eula.xml).
        HelpItem.Prose("permissions", R.string.permissions_notice),
        HelpItem.Prose("diagnostics", R.string.help_diagnostics),
        HelpItem.Prose("calibrate", R.string.help_calibrate),
        HelpItem.Prose("misc", R.string.help_misc, divider = true),
        HelpItem.Prose("hardware", R.string.help_hardware),
        HelpItem.Prose("troubleshooting", R.string.help_troubleshooting),
        HelpItem.Prose("pointer_mode", R.string.help_pointer_mode),
    )

/** The anchors [HELP_DOCUMENT] defines — the set `skymap://help#…` links may name. */
internal val HELP_ANCHORS: Set<String> = HELP_DOCUMENT.mapTo(mutableSetOf()) { it.anchor }
