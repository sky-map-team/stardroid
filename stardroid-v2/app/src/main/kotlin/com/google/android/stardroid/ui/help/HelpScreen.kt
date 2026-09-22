/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.help

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import com.google.android.stardroid.layers.SkyColors
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.ui.common.FoldedText
import com.google.android.stardroid.ui.common.StyledHtml
import com.google.android.stardroid.ui.common.WidgetsSheet
import com.google.android.stardroid.ui.common.matches
import com.google.android.stardroid.ui.common.rememberAssetBitmap
import com.google.android.stardroid.ui.common.topBarWindowInsets
import com.google.android.stardroid.ui.common.widgetOffers
import com.google.android.stardroid.ui.startup.appVersionName
import com.google.android.stardroid.ui.theme.documentColors
import com.google.android.stardroid.ui.theme.toComposeColor
import kotlinx.coroutines.launch

/**
 * v1 `HelpDialogFragment`, grown up into a full-screen destination: the help document rendered
 * natively via [StyledHtml] instead of a WebView (D48), with v1's `help.css` heading accents.
 * The What's New and Credits content lives on its own [WhatsNewScreen] (D74) rather than being
 * appended here.
 *
 * The document is a filterable list of [HELP_DOCUMENT] sections rather than one long run of
 * prose: users reported it as "very long and unsearchable" (#1030), and its own sentences now
 * link into the app, so it needs both a search box and named anchors to scroll to.
 *
 * [onNavigate] handles the links that leave Help. The two that don't — the widget catalogue
 * and in-document anchors — are handled here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    nightMode: Boolean,
    onBack: () -> Unit,
    onNavigate: (HelpLink.Destination) -> Unit,
    experimentConfig: ExperimentConfig = ExperimentConfig.Static,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var query by rememberSaveable { mutableStateOf("") }
    var showWidgetsSheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // An empty offer list means the widget components are gated off entirely, so the link has
    // nothing to show; leave it inert rather than open an empty sheet.
    val offers = widgetOffers(experimentConfig)
    val sections = rememberHelpSections()
    val rows = remember(sections, query) { helpRows(sections, query) }

    val onLink: (String) -> Unit = { url ->
        when (val link = parseHelpLink(url)) {
            // An unrecognised target is inert rather than fatal — see parseHelpLink.
            null -> Unit
            is HelpLink.Widgets -> if (offers.isNotEmpty()) showWidgetsSheet = true
            is HelpLink.Anchor -> {
                // Anchored prose is only reachable in the unfiltered document, so clear the
                // filter and look the target up in the rows that clearing it produces — the
                // list has a header of its own, so a section's row index is not its index in
                // HELP_DOCUMENT.
                query = ""
                val index = helpRows(sections, "").indexOfAnchor(link.section)
                if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
            }
            is HelpLink.Destination -> onNavigate(link)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.help_dialog_title)) },
                windowInsets = topBarWindowInsets(stringResource(R.string.help_dialog_title)),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Always on show rather than behind an icon: not being able to find anything is
            // the complaint this box answers, so it should not itself need finding.
            SearchField(
                query = query,
                onQueryChange = {
                    query = it
                    scope.launch { listState.scrollToItem(0) }
                },
            )
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is HelpRow.Version ->
                            Text(
                                stringResource(R.string.help_version, appVersionName()),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        is HelpRow.NoResults ->
                            Text(
                                stringResource(R.string.help_search_no_results, query),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        is HelpRow.Section ->
                            when (row.section.item) {
                                is HelpItem.SymbolKey ->
                                    SymbolKeySection(nightMode, query, onLink)
                                is HelpItem.Prose ->
                                    StyledHtml(
                                        stringResource(row.section.item.html),
                                        nightMode = nightMode,
                                        highlight = query,
                                        onInternalLink = onLink,
                                    )
                            }
                        is HelpRow.BottomSpacer -> Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showWidgetsSheet) {
        WidgetsSheet(offers, onDismiss = { showWidgetsSheet = false })
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.help_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.help_search_clear),
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

/** A [HelpItem] paired with the text the search box matches against. */
private class HelpSection(
    val item: HelpItem,
    val searchable: FoldedText,
)

/**
 * One row of the lazy list. The version header and the empty-search notice are rows rather
 * than conditional `item {}` blocks so that a row's index is a property of the list itself —
 * an anchor link scrolls by index, and an index that silently shifted with the header's
 * presence landed a section early.
 */
private sealed interface HelpRow {
    val key: String

    data object Version : HelpRow {
        override val key = "version"
    }

    data class Section(
        val section: HelpSection,
    ) : HelpRow {
        override val key = section.item.anchor
    }

    data object NoResults : HelpRow {
        override val key = "no-results"
    }

    data object BottomSpacer : HelpRow {
        override val key = "bottom-spacer"
    }
}

/**
 * The document as rows, for [query]. A blank query is the whole document behind its version
 * header; anything else drops the `<h1>` dividers and the sections that don't match.
 */
private fun helpRows(
    sections: List<HelpSection>,
    query: String,
): List<HelpRow> =
    buildList {
        if (query.isBlank()) {
            add(HelpRow.Version)
            sections.forEach { add(HelpRow.Section(it)) }
        } else {
            sections
                .filter { !it.item.isDivider() && it.searchable.matches(query) }
                .forEach { add(HelpRow.Section(it)) }
            if (none { it is HelpRow.Section }) add(HelpRow.NoResults)
        }
        add(HelpRow.BottomSpacer)
    }

private fun List<HelpRow>.indexOfAnchor(anchor: String): Int =
    indexOfFirst { it is HelpRow.Section && it.section.item.anchor == anchor }

private fun HelpItem.isDivider(): Boolean = this is HelpItem.Prose && divider

/**
 * The document's searchable text, folded once per locale rather than per keystroke. Prose folds
 * its rendered text — markup is not something a reader can search for — and the symbol legend
 * folds its heading and the labels beside each glyph.
 */
@Composable
private fun rememberHelpSections(): List<HelpSection> {
    val strings = HELP_DOCUMENT.map { item -> searchableTextOf(item) }
    return remember(strings) {
        HELP_DOCUMENT.mapIndexed { index, item -> HelpSection(item, FoldedText.of(strings[index])) }
    }
}

@Composable
private fun searchableTextOf(item: HelpItem): String =
    when (item) {
        is HelpItem.Prose -> AnnotatedString.fromHtml(stringResource(item.html)).text
        is HelpItem.SymbolKey ->
            buildString {
                appendLine(stringResource(R.string.help_symbol_key_title))
                appendLine(stringResource(R.string.help_symbol_key_intro))
                for (entry in SYMBOL_KEY_ENTRIES) appendLine(stringResource(entry.label))
            }
    }

/**
 * The deep-sky symbol legend and the heading that titles it: each D62 marker rendered from the
 * same `assets/catalog/icons/` webps the map draws, tinted as the map tints them (Lens Blue for
 * DSO markers, red-shifted in night mode) so the key matches what the user sees in the sky.
 */
@Composable
private fun SymbolKeySection(
    nightMode: Boolean,
    query: String,
    onLink: (String) -> Unit,
) {
    val heading = stringResource(R.string.help_symbol_key_title)
    val intro = stringResource(R.string.help_symbol_key_intro)
    Column {
        StyledHtml(
            "<h2>$heading</h2><p>$intro</p>",
            nightMode = nightMode,
            highlight = query,
            onInternalLink = onLink,
        )
        SymbolKey(nightMode)
    }
}

@Composable
private fun SymbolKey(nightMode: Boolean) {
    // Day mode tints from the renderer's own palette so the legend cannot drift from the map;
    // night mode substitutes the chrome red, standing in for the backend's night transform.
    val override = documentColors(nightMode).symbolTintOverride
    val dsoTint = override ?: SkyColors.DEEP_SKY_ICON.toComposeColor()
    val plainTint = override ?: Color.White
    Column(modifier = Modifier.padding(top = 4.dp)) {
        for (entry in SYMBOL_KEY_ENTRIES) {
            SymbolRow(
                asset = entry.asset,
                label = stringResource(entry.label),
                tint = if (entry.dso) dsoTint else plainTint,
            )
        }
    }
}

@Composable
private fun SymbolRow(
    asset: String,
    label: String,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        val bitmap = rememberAssetBitmap("catalog/icons/$asset.webp")
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                // Decorative: the adjacent text is the accessible name.
                contentDescription = null,
                // Modulate, like the renderer's GL_MODULATE tint: the white glyph takes the
                // color, the baked dark halo stays dark.
                colorFilter = ColorFilter.tint(tint, BlendMode.Modulate),
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private data class SymbolKeyEntry(
    val asset: String,
    val label: Int,
    val dso: Boolean = true,
)

/** Mirror of `CatalogLayers.DSO_ICONS_BY_TYPE` plus the meteor-shower radiant markers. */
private val SYMBOL_KEY_ENTRIES =
    listOf(
        SymbolKeyEntry("galaxy", R.string.symbol_key_galaxy),
        SymbolKeyEntry("open_cluster", R.string.symbol_key_open_cluster),
        SymbolKeyEntry("globular_cluster", R.string.symbol_key_globular_cluster),
        SymbolKeyEntry("diffuse_nebula", R.string.symbol_key_diffuse_nebula),
        SymbolKeyEntry("planetary_nebula", R.string.symbol_key_planetary_nebula),
        SymbolKeyEntry("supernova_remnant", R.string.symbol_key_supernova_remnant),
        SymbolKeyEntry("asterism", R.string.symbol_key_asterism),
        SymbolKeyEntry("other", R.string.symbol_key_other),
        SymbolKeyEntry("meteor_radiant", R.string.symbol_key_meteor_radiant, dso = false),
        SymbolKeyEntry(
            "meteor_radiant_peak",
            R.string.symbol_key_meteor_radiant_peak,
            dso = false,
        ),
    )
