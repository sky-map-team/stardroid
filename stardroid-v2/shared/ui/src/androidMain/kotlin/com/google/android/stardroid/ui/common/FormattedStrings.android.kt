/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import java.util.Locale

// Exactly what Resources.getString(id, *args) does, so Android's text does not change.
internal actual fun formatForLocale(
    template: String,
    args: Array<out Any>,
): String = String.format(Locale.getDefault(), template, *args)
