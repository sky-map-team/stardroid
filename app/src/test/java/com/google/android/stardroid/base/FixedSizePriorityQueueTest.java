// Copyright 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.base;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Unit tests for the FixedSizePriorityQueue class.
 * 
 * @author Brent Bryan
 */
public class FixedSizePriorityQueueTest extends TestCase {
  FixedSizePriorityQueue<Integer> queue;
  Filter<Integer> evenFilter;
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
   
    Comparator<Integer> comparator = new Comparator<Integer>() {
      public int compare(Integer object1, Integer object2) {
        if (object1.intValue()  == object2.intValue()) {
          return 0;
        } 
        
        return (object1.intValue() < object2.intValue()) ? -1 : 1; 
      }
    };
    
    queue = new FixedSizePriorityQueue<Integer>(10, comparator);
    
    evenFilter = new Filter<Integer>() {
      public boolean accept(Integer object) {
        return (object.intValue() % 2) == 0;
      }
    };
  }

  public void testAdd_noFilter() {
    assertEquals(null, queue.getFilter());
    
    assertEquals(0, queue.size());
    for (int i=0; i<10; i++) {
      assertFalse(queue.isFull());
      assertTrue(queue.add(i));
      assertEquals(i+1, queue.size());
    }
    assertEquals(0, queue.peek().intValue());
    assertTrue(queue.isFull());
    
    // Queue is full, should not be able to add a new lesser value
    assertFalse(queue.add(-1));
    assertEquals(0, queue.peek().intValue());
    assertEquals(10, queue.size());
    assertFalse(queue.isEmpty());
    assertTrue(queue.isFull());
    
    assertTrue(queue.add(10));
    assertEquals(1, queue.peek().intValue());
    assertEquals(10, queue.size());
    
    assertTrue(queue.add(5));
    assertEquals(2, queue.peek().intValue());
    assertEquals(10, queue.size());
  }
  
  public void testAdd_filter() {
    queue.setFilter(evenFilter);
    
    Integer[] numbers = {6, 8, 10, 12, 14, 16, 18, 20, 22};
    assertTrue(queue.addAll(Arrays.asList(numbers)));
    assertEquals(6, queue.peek().intValue());
    assertEquals(9, queue.size());

    assertFalse(queue.add(5));
    assertEquals(6, queue.peek().intValue());
    assertEquals(9, queue.size());

    assertTrue(queue.add(4));
    assertEquals(4, queue.peek().intValue());
    assertEquals(10, queue.size());

    assertFalse(queue.add(2));
    assertEquals(4, queue.peek().intValue());
    assertEquals(10, queue.size());

    assertFalse(queue.add(11));
    assertEquals(4, queue.peek().intValue());
    assertEquals(10, queue.size());
  }
  
  public void testAddAll_noFilter() {
    assertEquals(null, queue.getFilter());
    assertTrue(queue.isEmpty());
 
    Integer[] numbers = {2, 3, 4, 5, 10, 12, 18, 20};
    assertTrue(queue.addAll(Arrays.asList(numbers)));
    assertEquals(2, queue.peek().intValue());
    assertEquals(8, queue.size());
    assertFalse(queue.isEmpty());
    assertFalse(queue.isFull());
    
    Integer[] moreNumbers = {1, 7, 9};
    assertTrue(queue.addAll(Arrays.asList(moreNumbers)));
    assertEquals(2, queue.peek().intValue());
    assertEquals(10, queue.size());
    assertTrue(queue.isFull());

    Integer[] evenMoreNumbers = {0, 1, 0};
    assertFalse(queue.addAll(Arrays.asList(evenMoreNumbers)));
    assertEquals(2, queue.peek().intValue());
    assertEquals(10, queue.size());
  }
  
  public void testAddAll_filter() {
    queue.setFilter(evenFilter);
    assertNotNull(queue.getFilter());
    
    Integer[] numbers = {2, 4, 6, 8, 10, 12, 14, 16, 18, 20};
    assertTrue(queue.addAll(Arrays.asList(numbers)));
    assertEquals(2, queue.peek().intValue());
    assertEquals(10, queue.size());

    Integer[] moreNumbers = {0, 6, 13};
    assertTrue(queue.addAll(Arrays.asList(moreNumbers)));
    assertEquals(4, queue.peek().intValue());
    assertEquals(10, queue.size());

    Integer[] evenMoreNumbers = {1, 11, 31};
    assertFalse(queue.addAll(Arrays.asList(evenMoreNumbers)));
    assertEquals(4, queue.peek().intValue());
    assertEquals(10, queue.size());
  } 
}
