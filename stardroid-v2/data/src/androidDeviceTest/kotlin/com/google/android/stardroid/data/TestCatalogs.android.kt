/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider

private val context: Context
    get() = ApplicationProvider.getApplicationContext()

/** Through the app's own factory, so Room copies the APK asset as it does in production. */
internal actual fun openBundledCatalog(): SkyMapDatabase {
    deleteBundledCatalog()
    return SkyMapDatabaseFactory.create(context)
}

internal actual fun deleteBundledCatalog() {
    context.deleteDatabase(SkyMapDatabaseFactory.DATABASE_NAME)
}
