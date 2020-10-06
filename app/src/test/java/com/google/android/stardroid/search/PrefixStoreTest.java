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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

/**
 * Tests for the PrefixStore
 *
 * @author John Taylor
 */
public class PrefixStoreTest extends TestCase {
  private PrefixStore prefixStore;

  @Override
  protected void setUp() {
    prefixStore = new PrefixStore();
    prefixStore.add("Foo");
    prefixStore.add("a");
    prefixStore.add("ab");
    prefixStore.add("abc");
    prefixStore.add("bc");
  }

  public void testNoResult() {
    assertTrue(prefixStore.queryByPrefix("Bar").isEmpty());
  }

  public void testOneResult() {
    Set<String> results = prefixStore.queryByPrefix("Fo");
    assertEquals(1, results.size());
    assertTrue(results.contains("Foo"));
  }

  public void testSeveralResults() {
    Set<String> results = prefixStore.queryByPrefix("ab");
    assertEquals(2, results.size());
    assertTrue(results.contains("ab"));
    assertTrue(results.contains("abc"));
  }

  public void testBulkLoad() {
    List<String> newWords = new ArrayList<String>();
    newWords.add("abcd");
    newWords.add("abcde");
    newWords.add("bcde");
    prefixStore.addAll(newWords);
    Set<String> results = prefixStore.queryByPrefix("ab");
    assertEquals(4, results.size());
    assertTrue(results.contains("abcd"));
    assertTrue(results.contains("abcde"));
  }

  public void testMixedCase() {
    prefixStore.add("ABCD");
    Set<String> results = prefixStore.queryByPrefix("Ab");
    assertEquals(3, results.size());
    assertTrue(results.contains("ab"));
    assertTrue(results.contains("abc"));
    assertTrue(results.contains("ABCD"));
  }
}
