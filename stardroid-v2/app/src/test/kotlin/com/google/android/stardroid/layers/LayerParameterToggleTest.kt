/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [LayerParameter] as a sealed hierarchy.
 *
 * Made sealed at the *second* parameter rather than the fifth, on the grounds that more toggles
 * are coming — meteor-shower alerts are the same shape and are expected to gain the same in-layer
 * control. These tests pin the two properties every subtype shares, since the persistence path
 * treats them uniformly.
 */
class LayerParameterToggleTest {
    @Test
    fun `a toggle persists as a boolean string, riding the existing preference path`() {
        // Not a new storage type: the D87/D91 wiring stores a String per key, and reusing it means
        // nothing downstream has to learn about booleans.
        assertThat(LayerParameter.Toggle("x", defaultOn = false).defaultValue).isEqualTo("false")
        assertThat(LayerParameter.Toggle("x", defaultOn = true).defaultValue).isEqualTo("true")
    }

    @Test
    fun `pass alerts default off`() {
        // Turning the satellite layer on to *look* is not the same act as asking to be
        // interrupted, so the alert is opt-in even once the layer is on.
        assertThat(LayerParameter.PASS_ALERTS_PARAMETER.defaultOn).isFalse()
        assertThat(LayerParameter.PASS_ALERTS_PARAMETER.key).isEqualTo("pass_alerts")
    }

    @Test
    fun `a choice still validates its default against its options`() {
        // The existing invariant must survive the refactor: a default outside the option set would
        // persist a value the UI can never show as selected.
        assertThrows<IllegalArgumentException> {
            LayerParameter.Choice("x", options = listOf("a", "b"), defaultOption = "c")
        }
    }

    @Test
    fun `both subtypes expose a key and a default through the interface`() {
        // The persistence path reads only these two, so they are what the sealing has to guarantee.
        val parameters: List<LayerParameter> =
            listOf(LayerParameter.DISC_SIZE_PARAMETER, LayerParameter.PASS_ALERTS_PARAMETER)
        assertThat(parameters.map { it.key }).containsExactly("disc_size", "pass_alerts")
        assertThat(parameters.map { it.defaultValue }).containsExactly("glyphs", "false")
    }

    @Test
    fun `the satellite layer declares its parameter once, and the registry references it`() {
        // D91: a parameter exists in exactly one place, and the registry points at the declaring
        // layer's list rather than copying it.
        assertThat(SatelliteLayer.PARAMETERS).containsExactly(LayerParameter.PASS_ALERTS_PARAMETER)
        assertThat(LayerRegistry.PARAMETERS)
            .contains(SatelliteLayer.LAYER_ID to LayerParameter.PASS_ALERTS_PARAMETER)
    }

    @Test
    fun `pass alerts declare that they need notification permission`() {
        // Without this the switch reads as on while POST_NOTIFICATIONS is ungranted and the alert
        // simply never arrives - a silent failure the user cannot diagnose. The declaration lives
        // on the parameter rather than being special-cased in the sheet, because shower alerts are
        // expected to become the same shape.
        assertThat(LayerParameter.PASS_ALERTS_PARAMETER.requiresNotificationPermission).isTrue()
    }

    @Test
    fun `a toggle that leads to no notification declares nothing`() {
        assertThat(LayerParameter.Toggle("x").requiresNotificationPermission).isFalse()
    }

    @Test
    fun `eclipse alerts default off and declare their own key`() {
        // Same reasoning as pass alerts: looking at the planets isn't asking to be interrupted.
        assertThat(LayerParameter.ECLIPSE_ALERTS_PARAMETER.defaultOn).isFalse()
        assertThat(LayerParameter.ECLIPSE_ALERTS_PARAMETER.key).isEqualTo("eclipse_alerts")
        assertThat(LayerParameter.ECLIPSE_ALERTS_PARAMETER.requiresNotificationPermission).isTrue()
    }

    @Test
    fun `the solar system layer declares both its parameters, and the registry references them`() {
        assertThat(SolarSystemLayer.PARAMETERS).containsExactly(
            LayerParameter.DISC_SIZE_PARAMETER,
            LayerParameter.ECLIPSE_ALERTS_PARAMETER,
        )
        assertThat(LayerRegistry.PARAMETERS)
            .contains(SolarSystemLayer.LAYER_ID to LayerParameter.ECLIPSE_ALERTS_PARAMETER)
    }
}
