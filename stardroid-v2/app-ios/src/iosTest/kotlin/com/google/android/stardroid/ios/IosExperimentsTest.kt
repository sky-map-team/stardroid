/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.FlagSource
import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class IosExperimentsTest {
    private val launchFlags = listOf(Experiment.CAMERA_AR, Experiment.SHARE_SKY)

    private class Server(
        private val value: Boolean?,
    ) : FlagSource by NoFlagSource {
        override fun serverValue(key: String): Boolean? = value
    }

    @Test
    fun arAndShareShipDark() {
        val config = iosExperiments(NoFlagSource)
        for (flag in launchFlags) assertThat(config.isEnabled(flag)).isFalse()
    }

    @Test
    fun aServerWithNoValueLeavesThemDark() {
        val config = iosExperiments(Server(null))
        for (flag in launchFlags) assertThat(config.isEnabled(flag)).isFalse()
    }

    @Test
    fun theLaunchFlipReachesThem() {
        val config = iosExperiments(Server(true))
        for (flag in launchFlags) assertThat(config.isEnabled(flag)).isTrue()
    }

    @Test
    fun theKillSwitchReachesThem() {
        val config = iosExperiments(Server(false))
        for (flag in launchFlags) assertThat(config.isEnabled(flag)).isFalse()
    }
}
