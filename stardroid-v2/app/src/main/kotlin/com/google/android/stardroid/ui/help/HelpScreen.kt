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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import com.google.android.stardroid.layers.SkyColors
import com.google.android.stardroid.ui.common.StyledHtml
import com.google.android.stardroid.ui.common.rememberAssetBitmap
import com.google.android.stardroid.ui.common.topBarWindowInsets
import com.google.android.stardroid.ui.startup.appVersionName
import com.google.android.stardroid.ui.theme.documentColors
import com.google.android.stardroid.ui.theme.toComposeColor

/**
 * The help document, in render order. Each entry is one `<h2>` section (D78): the split keeps
 * a copy edit to one section from retranslating the whole document in every locale. Adding a
 * section means adding its key here and to `help.xml`.
 */
private val helpSections1 =
    intArrayOf(
        R.string.help_home_link,
        R.string.help_intro,
        R.string.help_navigating,
    )

private val helpSections2 =
    intArrayOf(
        R.string.help_layers,
        R.string.help_info_cards,
        R.string.help_search,
        R.string.help_time_travel,
        R.string.help_night_vision,
        R.string.help_other,
        R.string.help_gallery,
        R.string.help_location,
        // Lives in eula.xml, not help.xml: the terms screen renders the same key, so the
        // permission disclosure is written and translated exactly once (see eula.xml).
        R.string.permissions_notice,
        R.string.help_diagnostics,
        R.string.help_calibrate,
        R.string.help_misc,
        R.string.help_hardware,
        R.string.help_troubleshooting,
        R.string.help_pointer_mode,
    )

/**
 * v1 `HelpDialogFragment`, grown up into a full-screen destination: the help document rendered
 * natively via [StyledHtml] instead of a WebView (D48), with v1's `help.css` heading accents,
 * followed by the deep-sky symbol key. The What's New and Credits content lives on its own
 * [WhatsNewScreen] (D74) rather than being appended here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    nightMode: Boolean,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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
        val version = stringResource(R.string.help_version, appVersionName())
        val symbolsHeading = stringResource(R.string.help_symbol_key_title)
        val symbolsIntro = stringResource(R.string.help_symbol_key_intro)
        // The parse is remembered inside StyledHtml; the concat here is cheap.
        val helpBody1 = StringBuilder()
        for (section in helpSections1) {
            helpBody1.append(stringResource(section))
        }
        val html1 =
            "<p>$version</p>" +
                helpBody1 +
                "<h2>$symbolsHeading</h2><p>$symbolsIntro</p>"
        val helpBody2 = StringBuilder()
        for (section in helpSections2) {
            helpBody2.append(stringResource(section))
        }
        val html2 = helpBody2.toString()
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            StyledHtml(html1, nightMode = nightMode)
            SymbolKey(nightMode)
            StyledHtml(html2, nightMode = nightMode)
        }
    }
}

/**
 * The deep-sky symbol legend: each D62 marker rendered from the same
 * `assets/catalog/icons/` webps the map draws, tinted as the map tints them (Lens Blue for
 * DSO markers, red-shifted in night mode) so the key matches what the user sees in the sky.
 */
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
