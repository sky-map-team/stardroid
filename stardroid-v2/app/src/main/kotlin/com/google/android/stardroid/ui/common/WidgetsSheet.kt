/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.widget.CountdownWidgetReceiver
import com.google.android.stardroid.widget.MoonWidgetReceiver
import com.google.android.stardroid.widget.TonightWidgetReceiver

/**
 * One widget we can offer to place: the name and blurb the launcher's own picker shows for it,
 * so the in-app list and the system picker describe each widget with the same words, plus the
 * receiver [AddWidgetButton] pins.
 */
data class WidgetOffer(
    @StringRes val label: Int,
    @StringRes val description: Int,
    val receiver: Class<*>,
)

/**
 * The widgets currently on offer, in the order they were built. Gated like the components
 * themselves ([com.google.android.stardroid.widget.WidgetGate]): a disabled receiver can't be
 * pinned, so offering it would only lead to the manual-instructions dialog. An empty list means
 * nothing is offerable — callers hide their entry point rather than open an empty sheet.
 */
fun widgetOffers(experimentConfig: ExperimentConfig): List<WidgetOffer> =
    buildList {
        if (experimentConfig.isEnabled(Experiment.MOON_WIDGET)) {
            add(
                WidgetOffer(
                    R.string.moon_widget_label,
                    R.string.moon_widget_description,
                    MoonWidgetReceiver::class.java,
                ),
            )
        }
        if (experimentConfig.isEnabled(Experiment.TONIGHT_WIDGET)) {
            add(
                WidgetOffer(
                    R.string.tonight_widget_label,
                    R.string.tonight_widget_description,
                    TonightWidgetReceiver::class.java,
                ),
            )
            add(
                WidgetOffer(
                    R.string.countdown_widget_label,
                    R.string.countdown_widget_description,
                    CountdownWidgetReceiver::class.java,
                ),
            )
        }
    }

/**
 * The widget catalogue: one add-row per [WidgetOffer]. The single rendering of the list, so a
 * fourth widget is added in [widgetOffers] alone.
 */
@Composable
fun WidgetOfferList(
    offers: List<WidgetOffer>,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        for (offer in offers) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(offer.label),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(offer.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AddWidgetButton(offer.receiver)
            }
        }
    }
}

/**
 * Zone C's widgets sheet: the catalogue reached from the ⋮ menu, giving the Tonight and
 * Countdown widgets a discovery path of their own — the Moon's card promo row
 * ([com.google.android.stardroid.ui.objectinfo.MoonWidgetPromoRow]) only ever offered the Moon,
 * and only to someone already reading its info card. Help's widgets section links here too,
 * via `skymap://widgets`, rather than repeating the list inline.
 *
 * Sibling of [com.google.android.stardroid.ui.map.OverflowSheet] rather than a destination:
 * adding a widget hands off to the system's own pin dialog, so there is nothing here to
 * navigate back from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsSheet(
    offers: List<WidgetOffer>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.widgets_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.widgets_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            WidgetOfferList(offers)
        }
    }
}
