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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility methods for easily dealing with Lists.
 *
 * @author Brent Bryan
 */

public class Lists {
  // No Instances
  private Lists() {}

  /**
   * Transforms each element in the given Iterable and returns the result as a
   * List. Does not change the given Iterable, or the items stored therein.
   */
  public static <E, F> List<F> transform(Iterable<E> iterable, Transform<E, F> transform) {
    List<F> result = new ArrayList<>();
    for (E e : iterable) {
      result.add(transform.transform(e));
    }
    return result;
  }

  /**
   * Returns the given Iterable as a List. If the current Iterable is already a
   * List, then the Iterable is returned directly. Otherwise a new List is
   * created with the same elements as the given Iterable.
   */
  public static <E> List<E> asList(Iterable<E> iterable) {
    if (iterable instanceof List) {
      return (List<E>) iterable;
    }

    List<E> result = new ArrayList<E>();
    for (E e : iterable) {
      result.add(e);
    }
    return result;
  }

  /**
   * Converts a user specified set of objects into a {@link List} of that type.
   */
  public static <E> List<E> asList(E... objects) {
    return Arrays.asList(objects);
  }
}
