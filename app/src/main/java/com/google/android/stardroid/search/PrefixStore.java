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

package com.google.android.stardroid.search;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Given a set of strings such as search terms, this class allows you to search
 * for that string by prefix.
 * @author John Taylor
 *
 */
public class PrefixStore {
  // This is a naive implementation that simply stores all the prefixes in a
  // hashmap.  A better solution would be to use a trie.
  // TODO(johntaylor): reimplement this, probably as a trie.
  private HashMap<String, HashSet<String>> store = new HashMap<String, HashSet<String>>();
  private static final Set<String> EMPTY_SET = Collections.unmodifiableSet(new HashSet<String>());

  /**
   * Search for any queries matching this prefix.  Note that the prefix is
   * case-independent.
   */
  public Set<String> queryByPrefix(String prefix) {
    HashSet<String> results = store.get(prefix.toLowerCase());
    if (results == null) {
      return EMPTY_SET;
    }
    return results;
  }

  /**
   * Put a new string in the store.
   */
  public void add(String string) {
    // Add value to every prefix list.  Not exactly space-efficient, but time's
    // getting on.
    for (int i = 0; i < string.length(); ++i) {
      String prefix = string.substring(0, i + 1).toLowerCase();
      HashSet<String> currentList = store.get(prefix);
      if (currentList == null) {
        currentList = new HashSet<String>();
        store.put(prefix.toLowerCase(), currentList);
      }
      currentList.add(string);
    }
  }

  /**
   * Put a whole load of objects in the store at once.
   * @param strings a collection of strings.
   */
  public void addAll(Collection<String> strings) {
    for (String string : strings) {
      add(string);
    }
  }
}
