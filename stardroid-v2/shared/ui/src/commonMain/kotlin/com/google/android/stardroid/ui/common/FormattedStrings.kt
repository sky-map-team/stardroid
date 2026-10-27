/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A shared string formatted as Android's `getString(id, *args)` formats it. Compose resources'
 * own `stringResource(resource, *args)` replaces only `%1$s` and `%1$d`, and the strings
 * also use `%1$.1f`, `%+.1f`, `%02d` and `%%` — so shared screens format through this.
 */
@Composable
fun formattedStringResource(
    resource: StringResource,
    vararg args: Any,
): String = formatForLocale(stringResource(resource), args)

/** `String.format` in the app's locale: Java's own on Android, [formatAndroidStyle] elsewhere. */
internal expect fun formatForLocale(
    template: String,
    args: Array<out Any>,
): String
