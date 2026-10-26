/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.help

import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.help_calibrate
import com.google.android.stardroid.ui.resources.help_diagnostics
import com.google.android.stardroid.ui.resources.help_gallery
import com.google.android.stardroid.ui.resources.help_hardware
import com.google.android.stardroid.ui.resources.help_home_link
import com.google.android.stardroid.ui.resources.help_info_cards
import com.google.android.stardroid.ui.resources.help_intro
import com.google.android.stardroid.ui.resources.help_layers
import com.google.android.stardroid.ui.resources.help_location
import com.google.android.stardroid.ui.resources.help_misc
import com.google.android.stardroid.ui.resources.help_navigating
import com.google.android.stardroid.ui.resources.help_night_vision
import com.google.android.stardroid.ui.resources.help_notifications
import com.google.android.stardroid.ui.resources.help_other
import com.google.android.stardroid.ui.resources.help_pointer_mode
import com.google.android.stardroid.ui.resources.help_search
import com.google.android.stardroid.ui.resources.help_time_travel
import com.google.android.stardroid.ui.resources.help_troubleshooting
import com.google.android.stardroid.ui.resources.help_widgets
import com.google.android.stardroid.ui.resources.permissions_notice
import org.jetbrains.compose.resources.StringResource

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
        val html: StringResource,
        /**
         * An `<h1>` divider carrying no prose of its own. Hidden while a search is active: a
         * bare "Miscellaneous and Troubleshooting" heading above a filtered list titles
         * nothing.
         */
        val divider: Boolean = false,
        /**
         * The experiment that gates this section, or null when it is always shown. A section
         * for a feature that is flagged off would document something the user cannot find.
         */
        val experiment: Experiment? = null,
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
        HelpItem.Prose("home", Res.string.help_home_link),
        HelpItem.Prose("intro", Res.string.help_intro),
        HelpItem.Prose("navigating", Res.string.help_navigating),
        HelpItem.SymbolKey,
        HelpItem.Prose("layers", Res.string.help_layers),
        HelpItem.Prose("info_cards", Res.string.help_info_cards),
        HelpItem.Prose("search", Res.string.help_search),
        HelpItem.Prose("time_travel", Res.string.help_time_travel),
        HelpItem.Prose("night_vision", Res.string.help_night_vision),
        HelpItem.Prose("other", Res.string.help_other, divider = true),
        HelpItem.Prose("gallery", Res.string.help_gallery),
        HelpItem.Prose("widgets", Res.string.help_widgets),
        HelpItem.Prose(
            "notifications",
            Res.string.help_notifications,
            experiment = Experiment.NOTIFICATIONS,
        ),
        HelpItem.Prose("location", Res.string.help_location),
        // Lives in eula.xml, not help.xml: the terms screen renders the same key, so the
        // permission disclosure is written and translated exactly once (see eula.xml).
        HelpItem.Prose("permissions", Res.string.permissions_notice),
        HelpItem.Prose("diagnostics", Res.string.help_diagnostics),
        HelpItem.Prose("calibrate", Res.string.help_calibrate),
        HelpItem.Prose("misc", Res.string.help_misc, divider = true),
        HelpItem.Prose("hardware", Res.string.help_hardware),
        HelpItem.Prose("troubleshooting", Res.string.help_troubleshooting),
        HelpItem.Prose("pointer_mode", Res.string.help_pointer_mode),
    )

/** The anchors [HELP_DOCUMENT] defines — the set `skymap://help#…` links may name. */
internal val HELP_ANCHORS: Set<String> = HELP_DOCUMENT.mapTo(mutableSetOf()) { it.anchor }

/**
 * [HELP_DOCUMENT] minus the sections whose experiment is off. [HELP_ANCHORS] deliberately stays
 * the full set: a link to a hidden section parses fine and then simply finds nothing to scroll to.
 */
internal fun helpDocument(experimentConfig: ExperimentConfig): List<HelpItem> =
    HELP_DOCUMENT.filter { item ->
        item !is HelpItem.Prose ||
            item.experiment == null ||
            experimentConfig.isEnabled(item.experiment)
    }
