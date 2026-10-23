/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.help

import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HelpDocumentTest {
    private fun anchors(config: ExperimentConfig) = helpDocument(config).map { it.anchor }

    @Test
    fun `notifications section is hidden while the experiment is off`() {
        assertThat(anchors { false }).doesNotContain("notifications")
    }

    @Test
    fun `notifications section is shown when the experiment is on`() {
        assertThat(anchors { it == Experiment.NOTIFICATIONS }).contains("notifications")
    }

    @Test
    fun `ungated sections are unaffected by the flag`() {
        assertThat(anchors { true }.minus("notifications")).isEqualTo(anchors { false })
    }
}
