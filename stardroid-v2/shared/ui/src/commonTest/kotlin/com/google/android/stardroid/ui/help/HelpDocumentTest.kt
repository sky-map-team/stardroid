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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HelpDocumentTest {
    private fun anchors(config: ExperimentConfig) = helpDocument(config).map { it.anchor }

    @Test
    fun `notifications section is hidden while the experiment is off`() {
        assertFalse("notifications" in anchors { false })
    }

    @Test
    fun `notifications section is shown when the experiment is on`() {
        assertContains(anchors { it == Experiment.NOTIFICATIONS }, "notifications")
    }

    @Test
    fun `ungated sections are unaffected by the flag`() {
        assertEquals(anchors { false }, anchors { true }.minus("notifications"))
    }
}
