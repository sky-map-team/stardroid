// Copyright 2010 Google Inc.
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

package com.google.android.stardroid.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

/**
 * Unit tests for the WeakHashSet class.
 *
 * @author John Taylor
 */
public class WeakHashSetTest extends TestCase {
  private WeakHashSet<String> set;

  public void setUp() {
    set = new WeakHashSet<String>();
  }

  /**
   * Test method for
   * {@link com.google.android.stardroid.util.WeakHashSet#size()}.
   */
  public void testSize() {
    String one = "one";
    set.add(one);
    String two = "two";
    set.add(two);
    assertEquals(2, set.size());

    set.clear();
    assertTrue(set.isEmpty());
    assertEquals(0, set.size());
  }

  /**
   * Test method for
   * {@link com.google.android.stardroid.util.WeakHashSet#add(java.lang.Object)}
   * .
   */
  public void testAdd() {
    String one = "one";
    set.add(one);
    assertTrue(set.contains(one));
    assertFalse(set.contains("two"));
  }

  /**
   * Test add two identical items.
   */
  public void testAddTwo() {
    String one = "one";
    assertTrue(set.add(one));
    assertFalse(set.add(one));
    assertTrue(set.contains(one));
    assertEquals(1, set.size());
  }

  /**
   * Test that the references are weak. Note that this test might prove to be
   * flaky, since garbage collection cannot be guaranteed.
   * 
   * @throws InterruptedException
   */
  public void testWeak() throws InterruptedException {
    Set<Object> objectSet = new WeakHashSet<Object>();
    objectSet.add(new Object());
    // Try to force some garbage collection
    for (int i = 0; i < 1000000; ++i) {
      new Object();
    }
    System.gc();
    Thread.sleep(100);
    assertTrue(objectSet.isEmpty());
  }

  /**
   * Test method for
   * {@link java.util.AbstractSet#removeAll(java.util.Collection)}.
   */
  public void testRemoveAll() {
    String one = "one";
    String two = "two";
    String three = "three";
    set.add(one);
    set.add(two);
    set.add(three);
    List<String> list = new ArrayList<String>();
    list.add(one);
    list.add(three);
    set.removeAll(list);
    assertEquals(1, set.size());
    assertTrue(set.contains(two));
  }
}
