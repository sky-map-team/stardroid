/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    ListItem(
        headlineContent = { Text(stringResource(Res.string.support_tip_title)) },
        supportingContent = { Text(stringResource(Res.string.support_tip_desc)) },
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (tip in offered) {
            val name = stringResource(tipNames.getValue(tip.id))
            OutlinedButton(
                onClick = {
                    onAction(AnalyticsEvents.SUPPORT_ACTION_TIP)
                    store.purchase(tip.id) { result = it }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("$name ${tip.displayPrice}")
            }
        }
    }
    when (result) {
        TipResult.THANKS -> TipMessage(stringResource(Res.string.support_tip_thanks))
        TipResult.PENDING -> TipMessage(stringResource(Res.string.support_tip_pending))
        TipResult.FAILED -> TipMessage(stringResource(Res.string.support_tip_failed))
        TipResult.CANCELLED, null -> {}
    }
}

@Composable
private fun TipMessage(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
