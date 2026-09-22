/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.widget.CountdownWidgetReceiver
import com.google.android.stardroid.widget.MoonWidgetReceiver
import com.google.android.stardroid.widget.TonightWidgetReceiver
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The widget catalogue is gated by the same flags as the receivers themselves: a widget whose
 * flag is off has a disabled receiver and cannot be pinned, so offering it would lead only to
 * the manual-instructions dialog. With every flag off the list is empty, which is what hides
 * the overflow menu's Widgets row instead of opening an empty sheet.
 */
class WidgetOffersTest {
    private fun receiversFor(config: ExperimentConfig) = widgetOffers(config).map { it.receiver }

    @Test
    fun all_flags_off_offers_nothing() {
        assertThat(widgetOffers(ExperimentConfig { false })).isEmpty()
    }

    @Test
    fun moon_flag_offers_only_the_moon_widget() {
        assertThat(receiversFor(ExperimentConfig { it == Experiment.MOON_WIDGET }))
            .containsExactly(MoonWidgetReceiver::class.java)
    }

    @Test
    fun tonight_flag_offers_both_widgets_it_gates() {
        assertThat(receiversFor(ExperimentConfig { it == Experiment.TONIGHT_WIDGET }))
            .containsExactly(
                TonightWidgetReceiver::class.java,
                CountdownWidgetReceiver::class.java,
            )
    }

    @Test
    fun every_offer_carries_a_label_and_a_description() {
        for (offer in widgetOffers(ExperimentConfig { true })) {
            assertThat(offer.label).isNotEqualTo(0)
            assertThat(offer.description).isNotEqualTo(0)
        }
    }
}
