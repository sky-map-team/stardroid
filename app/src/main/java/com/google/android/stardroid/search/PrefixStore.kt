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

import java.util.*

/**
 * Given a set of strings such as search terms, this class allows you to search
 * for that string by prefix.
 * @author John Taylor
 */
class PrefixStore {
  private class TrieNode {
    var children: MutableMap<Char, TrieNode> = HashMap()

    // we need to store the originals to support insensitive case searching
    var results: MutableSet<String> = HashSet()
  }

  private var root = TrieNode()
  fun clear() {
    root = TrieNode()
  }

  /**
   * Search for any queries matching this prefix.  Note that the prefix is
   * case-independent.
   *
   * TODO(@tcao) refactor this API. Search should return a relevance ranked list.
   */
  fun queryByPrefix(prefix: String): Set<String> {
    val prefixLower = prefix.lowercase()
    var n = root
    for (element in prefixLower) {
      val c = n.children[element] ?: return EMPTY_SET
      n = c
    }
    val coll: MutableSet<String> = HashSet()
    collect(n, coll)
    return coll
  }

  private fun collect(n: TrieNode, coll: MutableCollection<String>) {
    coll.addAll(n.results)
    for (trieNode in n.children.values) {
      collect(trieNode, coll)
    }
  }

  /**
   * Put a new string in the store.
   */
  fun add(string: String) {
    var n = root
    val lower = string.lowercase()
    for (i in lower.indices) {
      var c = n.children[lower[i]]
      if (c == null) {
        c = TrieNode()
        n.children[lower[i]] = c
      }
      n = c
    }
    n.results.add(string)
  }

  /**
   * Put a whole load of objects in the store at once.
   * @param strings a collection of strings.
   */
  fun addAll(strings: Collection<String>) {
    for (string in strings) {
      add(string)
    }
  }

  companion object {
    private val EMPTY_SET = Collections.unmodifiableSet(HashSet<String>())
  }
}