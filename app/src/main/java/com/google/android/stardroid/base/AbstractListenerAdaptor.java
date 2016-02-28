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

import com.google.android.stardroid.util.WeakHashSet;
import com.google.common.base.Preconditions;

import java.util.Iterator;

/**
 * This class is a base class for objects which fire events to listeners.
 *
 * @param <E> Class of the listener that should be updated of events.
 * @author Brent Bryan
 */
public abstract class AbstractListenerAdaptor<E> {
  private WeakHashSet<E> listeners = new WeakHashSet<E>();

  protected abstract void fireNewListenerAdded(E listener);

  public void addListener(E listener) {
    Preconditions.checkNotNull(listener);
    if (listeners.add(listener)) {
      fireNewListenerAdded(listener);
    }
  }

  public void removeListener(E listener) {
    listeners.remove(listener);
  }

  public void removeAllListeners() {
    listeners.clear();
  }

  /**
   * Returns an upper bound on the number of listeners listening to this object.
   * The number of listeners returned is equal to the true number of listeners,
   * plus any listeners that have been garbage collected since the last time
   * getListeners() was called.
   */
  public int getNumListeners() {
    return listeners.size();
  }

  /**
   * Adds a WeakReference with no payload to the listeners map to ensure that
   * garbage collected listeners are correctly skipped.
   */
  @VisibleForTesting
  void addNullReference() {
    listeners.add(null);
  }

  /** Return an Iterable over the listeners referenced by this adaptor. */
  public Iterable<E> getListeners() {
    return new Iterable<E>() {
      public Iterator<E> iterator() {
        return listeners.iterator();
      }
    };
  }
}
