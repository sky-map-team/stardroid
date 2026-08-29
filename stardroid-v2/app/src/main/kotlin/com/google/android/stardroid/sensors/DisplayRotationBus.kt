/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The current display rotation, pushed by the visible activity (it recreates on rotation) so
 * the app-scoped sensor source remaps into the right display frame (D36).
 */
@Singleton
class DisplayRotationBus
    @Inject
    constructor() {
        val rotation = MutableStateFlow(Surface.ROTATION_0)
    }
