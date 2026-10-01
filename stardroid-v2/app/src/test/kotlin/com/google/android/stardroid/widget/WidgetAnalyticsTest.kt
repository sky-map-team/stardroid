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
import com.google.android.stardroid.analytics.FakeAnalytics
import com.google.android.stardroid.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Widget analytics: stable `widget` values, a guarded payload, and the opt-out gate. */
class WidgetAnalyticsTest {
    private val analytics = FakeAnalytics()
    private val settings = FakeSettings()

    @Test
    fun `each receiver has its own widget type`() {
        assertThat(widgetTypeOf(MoonWidgetReceiver::class.java))
            .isEqualTo(AnalyticsEvents.WIDGET_TYPE_MOON)
        assertThat(widgetTypeOf(TonightWidgetReceiver::class.java))
            .isEqualTo(AnalyticsEvents.WIDGET_TYPE_TONIGHT)
        assertThat(widgetTypeOf(CountdownWidgetReceiver::class.java))
            .isEqualTo(AnalyticsEvents.WIDGET_TYPE_COUNTDOWN)
    }

    @Test
    fun `an unrecognised receiver maps to a bounded value`() {
        assertThat(widgetTypeOf(String::class.java)).isEqualTo(AnalyticsEvents.WIDGET_TYPE_UNKNOWN)
    }

    @Test
    fun `params merge the extras with the widget type`() {
        val params = widgetEventParams("moon", mapOf("pin_source" to "moon_card"))

        assertThat(params)
            .containsExactly(AnalyticsEvents.WIDGET_TYPE, "moon", "pin_source", "moon_card")
    }

    @Test
    fun `an extra cannot overwrite the widget type`() {
        val params = widgetEventParams("moon", mapOf(AnalyticsEvents.WIDGET_TYPE to "other"))

        assertThat(params).containsExactly(AnalyticsEvents.WIDGET_TYPE, "moon")
    }

    @Test
    fun `logs the event when analytics is enabled`() =
        runTest {
            logWidgetEvent(analytics, settings, AnalyticsEvents.WIDGET_ADDED_EVENT, "tonight")

            assertThat(analytics.events)
                .containsExactly(
                    FakeAnalytics.Event(
                        AnalyticsEvents.WIDGET_ADDED_EVENT,
                        mapOf(AnalyticsEvents.WIDGET_TYPE to "tonight"),
                    ),
                )
        }

    @Test
    fun `logs nothing when the user has opted out`() =
        runTest {
            settings.enableAnalyticsState.value = false

            logWidgetEvent(analytics, settings, AnalyticsEvents.WIDGET_REMOVED_EVENT, "moon")

            assertThat(analytics.events).isEmpty()
        }
}
