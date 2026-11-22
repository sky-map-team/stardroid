/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.startup.FlagSource

internal actual fun createArCamera(): IosArCamera = AvArCamera()

internal actual fun flagSourceForTarget(source: FlagSource): FlagSource = source

internal actual fun orientationSourceForTarget(source: OrientationSource): OrientationSource =
    source
