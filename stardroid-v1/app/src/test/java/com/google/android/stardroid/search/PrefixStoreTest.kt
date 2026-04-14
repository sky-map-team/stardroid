// Copyright 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.search

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.*

/**
 * Tests for the PrefixStore
 *
 * @author John Taylor
 */
class PrefixStoreTest {
    private var prefixStore: PrefixStore? = null
    @Before
    fun setUp() {
        prefixStore = PrefixStore()
        prefixStore!!.add("Foo")
        prefixStore!!.add("a")
        prefixStore!!.add("ab")
        prefixStore!!.add("abc")
        prefixStore!!.add("bc")
    }

    @Test
    fun testNoResult() {
        assertThat(prefixStore!!.queryByPrefix("Bar")).isEmpty()
    }

    @Test
    fun testOneResult() {
        val results = prefixStore!!.queryByPrefix("Fo")
        assertThat(results).hasSize(1)
        assertThat(results).containsExactly("Foo")
    }

    @Test
    fun testSeveralResults() {
        val results = prefixStore!!.queryByPrefix("ab")
        assertThat(results).hasSize(2)
        assertThat(results).containsExactly("ab", "abc")
    }

    @Test
    fun testBulkLoad() {
        val newWords: MutableList<String> = ArrayList()
        newWords.add("abcd")
        newWords.add("abcde")
        newWords.add("bcde")
        prefixStore!!.addAll(newWords)
        val results = prefixStore!!.queryByPrefix("ab")
        assertThat(results).hasSize(4)
        assertThat(results).containsExactly("abcd", "abcde", "ab", "abc")
    }

    @Test
    fun testMixedCase() {
        prefixStore!!.add("ABCD")
        val results = prefixStore!!.queryByPrefix("Ab")
        assertThat(results).hasSize(3)
        assertThat(results).containsExactly("ab", "abc", "ABCD")
    }
}