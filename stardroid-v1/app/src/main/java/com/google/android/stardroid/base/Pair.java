// Copyright 2008 Google Inc.
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

import java.util.Comparator;

/**
 * A simple object which contains a pair of values.  This object can be stored and returned when
 * references to two objects are required.
 * 
 * @author Brent Bryan
 */
public class Pair <E, F> {
  private E first;
  private F second;
  
  public Pair(@Nullable E first, @Nullable F second) {
    this.first = first;
    this.second = second;
  }
  
  public E getFirst() {
    return first;
  }
  
  public void setFirst(@Nullable E first) {
    this.first = first;
  }
  
  public F getSecond() {
    return second;
  }
  
  public void setSecond(@Nullable F second) {
    this.second = second;
  }
  
  public static <S, T> Pair<S, T> of(S first, T second) {
    return new Pair<S, T>(first, second);
  }

  /** 
   * Returns a new comparator which compares the first object in a set of pairs using the 
   * specified Comparator.
   */
  public static <S> Comparator<Pair<S, ?>> comparatorOfFirsts(Comparator<S> comparator) {
    return new FirstComparator<S>(comparator);
  }
  
  private static class FirstComparator<E> implements Comparator<Pair<E, ?>> { 
    private final Comparator<E> comparator;
  
    public FirstComparator(Comparator<E> comparator) {
      this.comparator = comparator;
    }
  
    public int compare(Pair<E, ?> object1, Pair<E, ?> object2) {
      return comparator.compare(object1.getFirst(), object2.getFirst());
    }
  }
}
