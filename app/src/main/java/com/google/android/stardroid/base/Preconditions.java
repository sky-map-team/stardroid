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

/**
 * Static methods for asserting that parameters or other variables equal
 * specific values. This class is similar to the assert keyword in Java, except
 * that it is always on (it does not require an -ea flag). Moreover, this class
 * provides additional method for convenience.
 *
 * @author Brent Bryan
 */
public class Preconditions {

  /**
   * Ensures that the given boolean state is true. If false, throws a
   * PreconditionException with a generic error message.
   */
  public static void check(boolean state) {
    check(state, "Unexpected State Encountered");
  }

  /**
   * Ensures that the given boolean state is true. If false, throws a
   * PreconditionException with the given error message.
   */
  public static void check(boolean state, String message) {
    checkNotNull(message);
    if (!state) {
      throw new PreconditionException(message);
    }
  }

  /**
   * Ensures that the given reference is non-null.
   */
  public static <T> T checkNotNull(@Nullable T observed) {
    if (observed == null) {
      throw new PreconditionException("Expected non-null, but got null.");
    }
    return observed;
  }

  /**
   * Ensures that the specified String is not null, or equal ot the empty string
   * ("") after all whitespace characters have been removed.
   */
  public static void checkNotEmpty(@Nullable String s) {
    if (s == null || s.trim().equals("")) {
      throw new PreconditionException("Observed string was empty: %s", s);
    }
  }

  /**
   * Ensures that the two specified objects are (object) equal. This will not
   * throw a Precondition check if two objects are ".equals", but not "==". If
   * you want to test "==" equals, use checkSame.
   */
  public static <T> void checkEqual(@Nullable T observed, @Nullable T expected) {
    if (observed == null) {
      if (expected != null) {
        throw new PreconditionException("Expected %s, but got: %s", expected, observed);
      }
    } else if (!observed.equals(expected)) {
      throw new PreconditionException("Expected %s, but got: %s", expected, observed);
    }
  }

  /**
   * Ensures that the two specified objects are not equals (either "==" or
   * ".equals").
   */
  public static <T> void checkNotEqual(@Nullable T observed, @Nullable T expected) {
    if (observed == null) {
      if (expected == null) {
        throw new PreconditionException("Expected %s and %s to be different", expected, observed);
      }
    } else if (observed.equals(expected)) {
      throw new PreconditionException("Expected %s and %s to be different", expected, observed);
    }
  }

  /**
   * Ensures that the given value is between the min and max values (inclusive).
   * That is, min <= value <= max
   */
  public static <T extends Comparable<T>> void checkBetween(T value, T min, T max) {
    if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
      throw new PreconditionException("Value (%s) is not within expected range [%s, %s]",
          value, min, max);
    }
  }
}