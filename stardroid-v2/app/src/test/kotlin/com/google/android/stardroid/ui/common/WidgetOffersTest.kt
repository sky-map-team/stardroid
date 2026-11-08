package com.google.android.stardroid.ui.common

import com.google.android.stardroid.widget.CountdownWidgetReceiver
import com.google.android.stardroid.widget.MoonWidgetReceiver
import com.google.android.stardroid.widget.TonightWidgetReceiver
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WidgetOffersTest {
    @Test
    fun offers_every_widget() {
        assertThat(widgetOffers.map { it.receiver })
            .containsExactly(
                MoonWidgetReceiver::class.java,
                TonightWidgetReceiver::class.java,
                CountdownWidgetReceiver::class.java,
            )
    }

    @Test
    fun every_offer_carries_a_label_and_a_description() {
        for (offer in widgetOffers) {
            assertThat(offer.label).isNotEqualTo(0)
            assertThat(offer.description).isNotEqualTo(0)
        }
    }
}
