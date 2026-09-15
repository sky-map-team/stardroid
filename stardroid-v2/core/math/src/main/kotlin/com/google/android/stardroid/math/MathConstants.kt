/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.math

import kotlin.math.PI

/**
 * Fundamental angular conversion constants for [`:core:math`]. Pure Kotlin, no Android, no
 * astronomy specifics beyond coordinate conventions (see docs/design/core-math-astronomy.md).
 */
const val DEGREES_TO_RADIANS: Double = PI / 180.0
const val RADIANS_TO_DEGREES: Double = 180.0 / PI
const val HOURS_TO_DEGREES: Double = 15.0
const val DEGREES_TO_HOURS: Double = 1.0 / 15.0
