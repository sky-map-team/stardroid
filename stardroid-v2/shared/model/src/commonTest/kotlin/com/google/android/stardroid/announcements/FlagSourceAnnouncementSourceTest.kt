/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

import com.google.android.stardroid.startup.FlagSource
import com.google.android.stardroid.testing.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FlagSourceAnnouncementSourceTest {
    private class FakeSource(
        override val canFetch: Boolean = true,
        var text: String? = null,
    ) : FlagSource {
        val fetches = mutableListOf<Boolean>()
        var fetched: String? = null
        var error: String? = null

        override fun serverValue(key: String): Boolean? = null

        override fun serverText(key: String): String? = text.takeIf { key == "announcements" }

        override val lastFetchTimeMillis: Long? = null

        override fun fetch(
            ignoreThrottle: Boolean,
            onDone: (updated: Boolean, error: String?) -> Unit,
        ) {
            fetches += ignoreThrottle
            if (error == null) text = fetched
            onDone(error == null, error)
        }
    }

    @Test
    fun refreshKeepsTheThrottleAndTheNewPayloadShowsAtOnce() =
        runTest {
            val remote = FakeSource()
            remote.fetched =
                """{"v":1,"messages":[{"id":"a","start":"2026-10-03T16:00:00Z",""" +
                """"end":"2026-10-04T10:00:00Z","surfaces":["interstitial"],""" +
                """"text":{"en":{"title":"T"}}}]}"""
            val source = FlagSourceAnnouncementSource(remote)
            assertThat(source.current()).isEmpty()

            source.refresh()

            assertThat(remote.fetches).containsExactly(false)
            assertThat(source.current().map { it.id }).containsExactly("a")
        }

    @Test
    fun aFailedRefreshKeepsWhatWasCached() =
        runTest {
            val cached =
                """{"v":1,"messages":[{"id":"cached","start":"2026-10-03T16:00:00Z",""" +
                    """"end":"2026-10-04T10:00:00Z","surfaces":["interstitial"],""" +
                    """"text":{"en":{"title":"T"}}}]}"""
            val remote = FakeSource(text = cached)
            remote.error = "offline"
            val source = FlagSourceAnnouncementSource(remote)

            source.refresh()

            assertThat(source.current().map { it.id }).containsExactly("cached")
        }

    @Test
    fun withoutAServerNothingIsFetched() =
        runTest {
            val remote = FakeSource(canFetch = false)
            FlagSourceAnnouncementSource(remote).refresh()
            assertThat(remote.fetches).isEmpty()
        }
}
