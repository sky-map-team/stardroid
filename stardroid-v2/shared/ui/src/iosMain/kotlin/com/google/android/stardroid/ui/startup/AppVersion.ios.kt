/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.startup

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle

/**
 * The version with its release name ("2.0.0:GDC"), as Android's versionName carries it. The
 * name is not part of CFBundleShortVersionString, which Apple limits to digits and periods.
 */
fun iosAppVersionName(): String {
    val info = NSBundle.mainBundle
    return info.objectForInfoDictionaryKey("SkyMapVersionName") as? String
        ?: info.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        ?: ""
}

@Composable
actual fun appVersionName(): String = iosAppVersionName()
