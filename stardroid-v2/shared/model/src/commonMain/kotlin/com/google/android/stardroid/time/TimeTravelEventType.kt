/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.time

/**
 * What a time-travel preset resolves against: now, the next sunrise/sunset or lunar phase from the
 * app clock, or a fixed instant. The presets themselves, with their localized labels, are
 * `:app`'s `TimeTravelEvents`; the screen logic needs only this.
 */
enum class TimeTravelEventType {
    NOW,
    NEXT_SUNRISE,
    NEXT_SUNSET,
    NEXT_FULL_MOON,
    NEXT_NEW_MOON,
    FIXED,
}
