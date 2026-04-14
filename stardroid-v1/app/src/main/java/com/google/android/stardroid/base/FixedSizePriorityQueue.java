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

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * A Priority Queue implementation which holds no more than a specified number
 * of elements. When the queue size exceeds this specified size, the root is
 * removed to ensure that the resulting size remains fixed.
 *
 * @param <E> type of object contained in the queue
 *
 * @author Brent Bryan
 */
public class FixedSizePriorityQueue<E> extends PriorityQueue<E> {
  private static final long serialVersionUID = 3959389634971824728L;

  /** Maximum number of elements stored in this queue. */
  private int maxSize;

  /**
   * Filter used to reject some objects without even checking the number of
   * objects or priorities of those objects in the queue.
   */
  private Filter<? super E> filter = null;

  public FixedSizePriorityQueue(int maxQueueSize, Comparator<? super E> comparator) {
    super(maxQueueSize, comparator);
    this.maxSize = maxQueueSize;
  }

  /**
   * Sets the filter used to reject objects (without checking the number of
   * elements in the queue, or their priorities). Setting the filter to null
   * removes all filtering.
   */
  public void setFilter(@Nullable Filter<? super E> filter) {
    this.filter = filter;
  }

  /**
   * Returns the filter that is currently being used to reject elements which
   * are submitted for addition to the queue. Returns null if not filter has
   * been set.
   */
  public Filter<? super E> getFilter() {
    return filter;
  }

  @Override
  public boolean add(E object) {
    if (filter != null && !filter.accept(object)) {
      return false;
    }

    if (!isFull()) {
      super.add(object);
      return true;
    }

    if (comparator().compare(object, peek()) > 0) {
      poll();
      super.add(object);
      return true;
    }
    return false;
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    boolean changed = false;
    for (E e : c) {
      changed |= add(e);
    }
    return changed;
  }

  public boolean isFull() {
    return size() == maxSize;
  }
}
