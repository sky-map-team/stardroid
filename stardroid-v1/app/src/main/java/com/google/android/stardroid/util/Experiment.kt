/*
 * Copyright (c) 2026 Penterakt LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.util

enum class Experiment(val remoteConfigKey: String) {
    WARM_WELCOME("warm_welcome_enabled"),

    /**
     * Fraction (0..1) of the screen, centered, in which taps may open an object info card.
     * Taps outside this central box are ignored for info-card purposes. A value >= 1 makes the
     * whole screen active (disables the restriction).
     */
    INFO_CARD_TAP_REGION_FRACTION("info_card_tap_region_fraction"),

    /**
     * Maximum height of an object info card as a fraction (0..1) of the screen height. Cards
     * shorter than this still shrink to fit; taller cards are capped and scroll internally.
     */
    INFO_CARD_MAX_HEIGHT_FRACTION("info_card_max_height_fraction")
}
