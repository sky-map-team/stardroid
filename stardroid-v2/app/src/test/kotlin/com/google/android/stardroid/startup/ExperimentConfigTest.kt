/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.startup

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The flag set lives in two places that must agree: [ExperimentConfig.Static] (the fdroid
 * answer and every unit test's default) and the gms `remote_config_defaults.xml` shipped to
 * Firebase. Adding an [Experiment] without its XML entry is silent — Remote Config answers
 * the static empty config for the unknown key — so pin the correspondence here.
 */
class ExperimentConfigTest {
    private val defaultsXml =
        File("src/gms/res/xml/remote_config_defaults.xml").readText()

    private val shippedDefaults: Map<String, Boolean> =
        ENTRY.findAll(defaultsXml).associate { match ->
            match.groupValues[1].trim() to match.groupValues[2].trim().toBoolean()
        }

    @Test
    fun `every experiment has a shipped remote config default`() {
        assertThat(shippedDefaults.keys)
            .containsExactlyElementsIn(Experiment.entries.map { it.key })
    }

    @Test
    fun `shipped defaults agree with the static config`() {
        for (experiment in Experiment.entries) {
            assertThat(shippedDefaults[experiment.key])
                .isEqualTo(ExperimentConfig.Static.isEnabled(experiment))
        }
    }

    @Test
    fun `experiment keys are unique`() {
        assertThat(Experiment.entries.map { it.key }.toSet())
            .hasSize(Experiment.entries.size)
    }

    private companion object {
        val ENTRY =
            Regex("<entry>\\s*<key>(.*?)</key>\\s*<value>(.*?)</value>\\s*</entry>")
    }
}
