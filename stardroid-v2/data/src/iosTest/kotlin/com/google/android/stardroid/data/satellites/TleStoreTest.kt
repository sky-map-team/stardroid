/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import com.google.android.stardroid.testing.assertThat
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Instant
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails

/**
 * [TleStore] over iOS's [DirectoryTextFiles]: the Android file behaviour that
 * SatelliteElementsRepositoryTest pins on the JVM, checked on the iOS file system.
 */
@OptIn(ExperimentalForeignApi::class)
class TleStoreTest {
    private val directory = NSTemporaryDirectory() + "skymap-tle-store-test"
    private val store = TleStore(DirectoryTextFiles(directory))

    @BeforeTest
    @AfterTest
    fun clean() {
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    @Test
    fun nothingIsStoredAtFirst() {
        assertThat(store.readElements()).isNull()
        assertThat(store.readState()).isEqualTo(SatelliteFetchState())
    }

    @Test
    fun elementsRoundTripVerbatim() {
        val elements = "ISS (ZARYA)\n1 25544U 98067A\n2 25544  51.6331\n"
        store.writeElements(elements)
        assertThat(store.readElements()).isEqualTo(elements)
    }

    @Test
    fun stateRoundTrips() {
        val state =
            SatelliteFetchState(
                consecutiveFailures = 2,
                circuitOpenUntil = Instant.parse("2026-09-30T12:00:00Z"),
                lastAttempt = Instant.parse("2026-09-30T10:15:30.250Z"),
                lastSuccess = Instant.parse("2026-09-29T22:00:00Z"),
                elementsWrittenAt = Instant.parse("2026-09-29T22:00:00Z"),
                lastModified = "Tue, 29 Sep 2026 21:58:04 GMT",
                lastStatusCode = 503,
            )
        store.writeState(state)
        assertThat(store.readState()).isEqualTo(state)
    }

    @Test
    fun anUnparseableStateFileReadsAsNeverFetched() {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
        @Suppress("CAST_NEVER_SUCCEEDS")
        ("this is not a state file" as NSString)
            .writeToFile("$directory/fetch-state.txt", true, NSUTF8StringEncoding, null)
        assertThat(store.readState()).isEqualTo(SatelliteFetchState())
    }

    @Test
    fun aWriteThatCannotLandThrowsAndAnUnreadableFileReadsAsAbsent() {
        // A directory where the elements file should be, as on the JVM: the write cannot be moved
        // into place, so it must throw (the repository reports a StorageFailure), and reading
        // must degrade to "nothing cached" rather than throw.
        NSFileManager.defaultManager
            .createDirectoryAtPath("$directory/elements.tle", true, null, null)

        assertFails { store.writeElements("new elements") }
        assertThat(store.readElements()).isNull()
    }
}
