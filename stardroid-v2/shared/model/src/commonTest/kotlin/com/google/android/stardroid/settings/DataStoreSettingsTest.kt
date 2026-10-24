/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.settings

import com.google.android.stardroid.startup.DataStoreStartupState
import com.google.android.stardroid.testing.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Settings and startup state round-trip through a real DataStore file, on every platform. On iOS,
 * this is the store the app opens; Android opens the same store through its own factory.
 */
class DataStoreSettingsTest {
    private val path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY
            .resolve("settings-test-${Random.nextLong()}.preferences_pb")
            .toString()

    @AfterTest
    fun deleteFile() {
        FileSystem.SYSTEM.delete(path.toPath(), mustExist = false)
    }

    @Test
    fun valuesWrittenAreReadBackAndOutliveTheStore() =
        runTest {
            val firstJob = SupervisorJob()
            val settings =
                DataStoreSettings(
                    settingsDataStore(path, CoroutineScope(Dispatchers.IO + firstJob)),
                )
            assertThat(settings.nightMode.first()).isFalse()

            settings.setNightMode(true)
            settings.setFontSize(FontSize.LARGE)
            assertThat(settings.nightMode.first()).isTrue()
            assertThat(settings.fontSize.first()).isEqualTo(FontSize.LARGE)

            // A fresh store on the same file, once the first is gone, sees what was written.
            firstJob.cancelAndJoin()
            val reopened = DataStoreSettings(settingsDataStore(path))
            assertThat(reopened.nightMode.first()).isTrue()
            assertThat(reopened.fontSize.first()).isEqualTo(FontSize.LARGE)
        }

    @Test
    fun startupStateSharesTheFile() =
        runTest {
            val store = settingsDataStore(path)
            val startup = DataStoreStartupState(store)
            val settings = DataStoreSettings(store)

            startup.setEulaAcceptedVersion(3)
            settings.setNightMode(true)

            assertThat(startup.eulaAcceptedVersion.first()).isEqualTo(3)
            assertThat(settings.nightMode.first()).isTrue()
        }
}
