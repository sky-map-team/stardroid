/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [WidgetGate] against the real package manager (D75): the experiment flag is the only thing
 * that adds or removes a widget from the launcher's picker, and no JVM fake can say whether
 * the component state actually flipped.
 */
@RunWith(AndroidJUnit4::class)
class WidgetGateTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val receivers =
        arrayOf(
            MoonWidgetReceiver::class.java,
            TonightWidgetReceiver::class.java,
            CountdownWidgetReceiver::class.java,
        )

    private fun stateOf(receiver: Class<*>) =
        context.packageManager.getComponentEnabledSetting(ComponentName(context, receiver))

    /** Leaves the receivers on, as the shipped flags do, for whatever runs next. */
    @After
    fun restoreShippedState() {
        WidgetGate.apply(context, true, *receivers)
    }

    @Test
    fun disabling_removes_every_named_receiver() {
        WidgetGate.apply(context, false, *receivers)

        for (receiver in receivers) {
            assertThat(stateOf(receiver))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }
    }

    @Test
    fun enabling_restores_every_named_receiver() {
        WidgetGate.apply(context, false, *receivers)

        WidgetGate.apply(context, true, *receivers)

        for (receiver in receivers) {
            assertThat(stateOf(receiver))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        }
    }

    @Test
    fun a_flag_only_touches_its_own_receivers() {
        WidgetGate.apply(context, true, *receivers)

        // The tonight flag governs tonight + countdown; the moon widget must not move.
        WidgetGate.apply(
            context,
            false,
            TonightWidgetReceiver::class.java,
            CountdownWidgetReceiver::class.java,
        )

        assertThat(stateOf(MoonWidgetReceiver::class.java))
            .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        assertThat(stateOf(TonightWidgetReceiver::class.java))
            .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        assertThat(stateOf(CountdownWidgetReceiver::class.java))
            .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }
}
