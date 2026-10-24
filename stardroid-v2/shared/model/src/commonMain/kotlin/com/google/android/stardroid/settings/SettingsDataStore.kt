/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

/**
 * The app's one Preferences DataStore — settings and startup state share it — as a file at
 * [path], which must end in `.preferences_pb`. iOS opens it this way, under Application Support.
 * Android keeps `Context.preferencesDataStore(name = "settings")`, which is the same store in
 * the app's own directory.
 *
 * At most one DataStore may be open on a file per process: hold the result for the process's
 * lifetime.
 */
fun settingsDataStore(
    path: String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { path.toPath() })
