/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

/**
 * A choice a layer exposes to the user, shown as an inline expander under its row in the Layers
 * sheet (D87). The first is the solar system's disc size; grid density, magnitude limit and
 * shower selection are the obvious later customers.
 *
 * Keys, not string resources: a layer says *what* it offers and the sheet decides how to name it,
 * the same split the existing `layerName`/`layerIcon` mapping already uses. Keys are persisted,
 * so they are API — renaming one silently resets everybody's preference.
 *
 * Sealed because a parameter is not always a choice. [Toggle] arrived with satellite pass alerts
 * (D92 phase 4c), which are boolean: encoding them as a two-option `on`/`off` [Choice] would
 * render as a radio expander where a switch is meant. Made sealed at the *second* parameter rather
 * than the fifth, on the grounds that more toggles are coming — meteor-shower alerts are the same
 * shape and are expected to gain the same in-layer control.
 */
sealed interface LayerParameter {
    /**
     * The persisted preference key. **API**: renaming one silently resets everybody's setting.
     */
    val key: String

    /** The value in force when the user has never touched it. */
    val defaultValue: String

    /** An enumerated choice, rendered as a segmented row. */
    data class Choice(
        override val key: String,
        val options: List<String>,
        val defaultOption: String,
    ) : LayerParameter {
        override val defaultValue: String get() = defaultOption

        init {
            require(defaultOption in options) { "default $defaultOption is not one of $options" }
        }
    }

    /**
     * A boolean, rendered as a switch.
     *
     * Persisted as `"true"`/`"false"` strings rather than a new storage type, so it rides the
     * existing `layerParameter(id, key, default)` path unchanged — the D87/D91 wiring keeps
     * working and nothing new has to learn about booleans.
     */
    data class Toggle(
        override val key: String,
        val defaultOn: Boolean = false,
        /**
         * True when switching this on leads to a notification, so the UI must request
         * `POST_NOTIFICATIONS` (Android 13+) at the moment of opting in.
         *
         * Declared on the parameter rather than special-cased in the sheet because the next
         * toggle of this kind is already expected — shower alerts are the same shape — and a
         * switch that silently never notifies is the worst possible failure: it looks on, and the
         * user has no way to work out why nothing arrives.
         */
        val requiresNotificationPermission: Boolean = false,
    ) : LayerParameter {
        override val defaultValue: String get() = defaultOn.toString()
    }

    companion object {
        /** The Solar System layer's disc-size choice. */
        const val DISC_SIZE = "disc_size"

        /** True apparent size at every zoom, floored only so nothing vanishes. */
        const val DISC_SIZE_TRUE = "true"

        /**
         * v1's fixed angular sizes: legible at any zoom, never honest — and, because the size is
         * angular rather than a screen fraction, the mode that responds most to zooming. The
         * default, so the map opens looking as it always has and true scale is opt-in.
         */
        const val DISC_SIZE_GLYPHS = "glyphs"

        /** D86's floor: a constant share of the screen, handing off to true size when zoomed. */
        const val DISC_SIZE_AUTO = "auto"

        val DISC_SIZE_PARAMETER =
            Choice(
                key = DISC_SIZE,
                options = listOf(DISC_SIZE_TRUE, DISC_SIZE_GLYPHS, DISC_SIZE_AUTO),
                defaultOption = DISC_SIZE_GLYPHS,
            )

        /** The satellite layer's pass-alert opt-in (D92 phase 4c). */
        const val PASS_ALERTS = "pass_alerts"

        /**
         * Off by default, and deliberately separate from the layer's own on/off state: turning the
         * layer on to *look* at satellites is not the same act as asking to be interrupted about
         * them.
         */
        val PASS_ALERTS_PARAMETER =
            Toggle(
                key = PASS_ALERTS,
                defaultOn = false,
                requiresNotificationPermission = true,
            )

        /** The Solar System layer's lunar-eclipse reminder opt-in (D106). */
        const val ECLIPSE_ALERTS = "eclipse_alerts"

        /**
         * Off by default, same reasoning as [PASS_ALERTS_PARAMETER]: having the Solar System
         * layer on to see the planets is not the same act as asking to be reminded of an
         * eclipse.
         */
        val ECLIPSE_ALERTS_PARAMETER =
            Toggle(
                key = ECLIPSE_ALERTS,
                defaultOn = false,
                requiresNotificationPermission = true,
            )
    }
}
