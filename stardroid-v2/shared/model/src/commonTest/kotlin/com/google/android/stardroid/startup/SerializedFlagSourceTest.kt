/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.startup

import com.google.android.stardroid.testing.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SerializedFlagSourceTest {
    private class SlowSource : FlagSource {
        val throttleIgnored = mutableListOf<Boolean>()
        val replies = mutableListOf<(Boolean, String?) -> Unit>()

        override val canFetch: Boolean = true

        override fun serverValue(key: String): Boolean? = null

        override fun serverText(key: String): String? = null

        override val lastFetchTimeMillis: Long? = null

        override fun fetch(
            ignoreThrottle: Boolean,
            onDone: (updated: Boolean, error: String?) -> Unit,
        ) {
            throttleIgnored += ignoreThrottle
            replies += onDone
        }
    }

    @Test
    fun aForcedFetchWaitsForTheOneInFlightThenMakesItsOwn() =
        runTest {
            val slow = SlowSource()
            val source = SerializedFlagSource(slow, this)
            val results = mutableListOf<String>()
            source.fetch(false) { updated, _ -> results += "routine $updated" }
            source.fetch(true) { updated, _ -> results += "forced $updated" }
            runCurrent()
            assertThat(slow.throttleIgnored).containsExactly(false)

            slow.replies[0](false, null)
            runCurrent()
            assertThat(slow.throttleIgnored).containsExactly(false, true).inOrder()
            assertThat(results).containsExactly("routine false")

            slow.replies[1](true, null)
            runCurrent()
            assertThat(results).containsExactly("routine false", "forced true").inOrder()
        }

    @Test
    fun callersDuringAFetchShareOneFollowingFetchForcedIfAnyForcedIt() =
        runTest {
            val slow = SlowSource()
            val source = SerializedFlagSource(slow, this)
            var answered = 0
            source.fetch(false) { _, _ -> answered++ }
            runCurrent()
            repeat(20) { source.fetch(false) { _, _ -> answered++ } }
            source.fetch(true) { _, _ -> answered++ }
            runCurrent()

            slow.replies[0](false, null)
            runCurrent()
            assertThat(slow.throttleIgnored).containsExactly(false, true).inOrder()
            assertThat(answered).isEqualTo(1)

            slow.replies[1](true, null)
            runCurrent()
            assertThat(answered).isEqualTo(22)
        }

    @Test
    fun aFetchThatNeverAnswersIsNotOverlapped() =
        runTest {
            val slow = SlowSource()
            val source = SerializedFlagSource(slow, backgroundScope)
            var answered = 0
            repeat(5) { source.fetch(true) { _, _ -> answered++ } }
            runCurrent()

            assertThat(slow.replies.size).isEqualTo(1)
            assertThat(answered).isEqualTo(0)
        }

    @Test
    fun aRepeatedCallbackIsIgnored() =
        runTest {
            val slow = SlowSource()
            val source = SerializedFlagSource(slow, this)
            var answered = 0
            source.fetch(false) { _, _ -> answered++ }
            runCurrent()

            slow.replies[0](true, null)
            slow.replies[0](true, null)
            runCurrent()

            assertThat(answered).isEqualTo(1)
        }

    @Test
    fun aFailedFetchReleasesTheNext() =
        runTest {
            val slow = SlowSource()
            val source = SerializedFlagSource(slow, this)
            val errors = mutableListOf<String?>()
            source.fetch(false) { _, error -> errors += error }
            source.fetch(false) { _, error -> errors += error }
            runCurrent()

            slow.replies[0](false, "offline")
            runCurrent()
            slow.replies[1](false, null)
            runCurrent()

            assertThat(errors).containsExactly("offline", null).inOrder()
        }
}
