/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.support_tip_desc
import com.google.android.stardroid.ui.resources.support_tip_failed
import com.google.android.stardroid.ui.resources.support_tip_pending
import com.google.android.stardroid.ui.resources.support_tip_thanks
import com.google.android.stardroid.ui.resources.support_tip_title
import com.google.android.stardroid.ui.resources.tip_jupiter
import com.google.android.stardroid.ui.resources.tip_mars
import com.google.android.stardroid.ui.resources.tip_mercury
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A tip on offer; [displayPrice] is StoreKit's, already localized to the user's storefront. */
class TipOption(
    val id: String,
    val displayPrice: String,
)

enum class TipResult { THANKS, CANCELLED, PENDING, FAILED }

/**
 * The tip jar's store, implemented in Swift over StoreKit 2 (which has no Objective-C API for
 * Kotlin to call). Callbacks arrive on the main thread, and not at all while Compose is gone.
 */
interface TipStore {
    /** Only the products the store knows, cheapest first; none when offline or unconfigured. */
    fun loadTips(onLoaded: (List<TipOption>) -> Unit)

    fun purchase(
        id: String,
        onResult: (TipResult) -> Unit,
    )
}

private const val STACK_FONT_SCALE = 1.3f

private val tipNames: Map<String, StringResource> =
    mapOf(
        "tip.mercury" to Res.string.tip_mercury,
        "tip.mars" to Res.string.tip_mars,
        "tip.jupiter" to Res.string.tip_jupiter,
    )

/**
 * The Support screen's sponsoring row on iOS, where a tip must be an in-app purchase rather
 * than a link out. Hidden until StoreKit returns products, so it never shows empty buttons.
 */
@Composable
fun TipJarRow(
    store: TipStore,
    onAction: (String) -> Unit,
) {
    var tips by remember { mutableStateOf<List<TipOption>?>(null) }
    var result by remember { mutableStateOf<TipResult?>(null) }
    LaunchedEffect(store) { store.loadTips { tips = it } }
    val offered = tips.orEmpty().filter { it.id in tipNames }
    if (offered.isEmpty()) return

    // One container, so the buttons sit on the same surface as their heading.
    Column(Modifier.fillMaxWidth().background(ListItemDefaults.containerColor)) {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.support_tip_title)) },
            supportingContent = { Text(stringResource(Res.string.support_tip_desc)) },
        )
        // Name over price, three across; at large text sizes (or on a 320 pt screen) the
        // buttons would break words, so they stack instead.
        val stacked = LocalDensity.current.fontScale > STACK_FONT_SCALE
        val buttons: @Composable (Modifier) -> Unit = { modifier ->
            for (tip in offered) {
                TipButton(
                    name = stringResource(tipNames.getValue(tip.id)),
                    price = tip.displayPrice,
                    modifier = modifier,
                ) {
                    onAction(AnalyticsEvents.SUPPORT_ACTION_TIP)
                    store.purchase(tip.id) { result = it }
                }
            }
        }
        val padding = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        if (stacked) {
            Column(padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons(Modifier.fillMaxWidth())
            }
        } else {
            Row(padding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons(Modifier.weight(1f))
            }
        }
        when (result) {
            TipResult.THANKS -> TipMessage(stringResource(Res.string.support_tip_thanks))
            TipResult.PENDING -> TipMessage(stringResource(Res.string.support_tip_pending))
            TipResult.FAILED -> TipMessage(stringResource(Res.string.support_tip_failed))
            TipResult.CANCELLED, null -> {}
        }
    }
}

@Composable
private fun TipButton(
    name: String,
    price: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(price, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TipMessage(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
