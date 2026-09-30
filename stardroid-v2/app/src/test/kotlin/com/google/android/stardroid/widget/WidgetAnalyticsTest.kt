/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Each widget receiver maps to its own stable analytics `widget` value. */
class WidgetAnalyticsTest {
    @Test
    fun `each receiver has its own widget type`() {
        assertThat(widgetTypeOf(MoonWidgetReceiver::class.java))
            .isEqualTo(AnalyticsEvents.WIDGET_TYPE_MOON)
        assertThat(widgetTypeOf(TonightWidgetReceiver::class.java))
            .isEqualTo(AnalyticsEvents.WIDGET_TYPE_TONIGHT)
        assertThat(widgetTypeOf(CountdownWidgetReceiver::class.java))
            .isEqualTo(AnalyticsEvents.WIDGET_TYPE_COUNTDOWN)
    }
}
