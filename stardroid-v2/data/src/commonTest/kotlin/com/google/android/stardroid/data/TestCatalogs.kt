/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

/**
 * Opens a fresh on-device copy of the bundled catalog, the way the platform's app does on first
 * launch — whatever copy an earlier test left is discarded first.
 */
internal expect fun openBundledCatalog(): SkyMapDatabase

/** Deletes the copy [openBundledCatalog] made. Close the database first. */
internal expect fun deleteBundledCatalog()

/** An empty in-memory catalog, for tests that apply the fixture packs themselves. */
internal expect fun inMemoryCatalog(): SkyMapDatabase
