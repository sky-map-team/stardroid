// Copyright 2010 Google Inc.
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

package com.google.android.stardroid.test.util;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

/**
 * Unit tests for the {@link EqualsTester} class.
 *
 * @author Brent Bryan
 */
public class EqualsTesterTest extends TestCase {

  public void testEquals() {
    new EqualsTester().newEqualityGroup("a", "a").newEqualityGroup("b", "b").testEquals();
  }

  /**
   * Throws a new {@link AssertionFailedError} if calling
   * {@link EqualsTester#testEquals} on the given {@link EqualsTester} does not
   * cause it to throw an {@link AssertionFailedError}.
   */
  private void assertTestEqualsCausesException(String message, EqualsTester tester) {
    boolean caughtError = false;
    try {
      tester.testEquals();
    } catch (AssertionFailedError e) {
      caughtError = true;
    }
    if (!caughtError) {
      fail(message);
    }
  }

  /** Check 1) comparing each object against itself returns true */
  public void testEquals_objectNotEqualToSelf() {
    assertTestEqualsCausesException("Object not equaling itself should cause an exception.",
        new EqualsTester().newEqualityGroup(new Object() {
          @Override
          public boolean equals(Object o) {
            return this != o;
          }
          // Provided to make lint happy
          @Override
          public int hashCode() {
            return super.hashCode();
          }
        }));
  }

  /** Check 2) comparing each object against null returns false */
  public void testEquals_objectEqualToNull() {
    assertTestEqualsCausesException("Object equaling null should cause an exception.",
        new EqualsTester().newEqualityGroup(new Object() {
          @Override
          public boolean equals(Object o) {
            return this == o || o == null;
          }
          // Provided to make lint happy
          @Override
          public int hashCode() {
            return super.hashCode();
          }
        }));
  }

  /**
   * Check 3) comparing each object an instance of an incompatible class returns
   * false
   */
  public void testEquals_objectEqualToIncompatibleClass() {
    assertTestEqualsCausesException(
        "Object equaling an incompatible class should cause an exception.",
        new EqualsTester().newEqualityGroup(new Object() {
          @Override
          public boolean equals(Object o) {
            return o != null;
          }
          // Provided to make lint happy
          @Override
          public int hashCode() {
            return super.hashCode();
          }
        }));
  }

  /**
   * Check 4) comparing each pair of objects within the same equality group
   * returns true
   */
  public void testEquals_unequalObjectsInEqualityGroup() {
    assertTestEqualsCausesException(
        "Unequal objects in the same equality group should cause an exception.",
        new EqualsTester().newEqualityGroup("a", "b"));
  }

  /** An object with a user defined hash code value. */
  static class DefinedHashCodeObject {
    private int hashCode;

    public DefinedHashCodeObject(int hashCode) {
      this.hashCode = hashCode;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof DefinedHashCodeObject && o != null;
    }
  }

  /** Check 5) the hash code of any two equal objects are equal */
  public void testEquals_equalObjectsHaveUnequalHashCodes() {

    assertTestEqualsCausesException(
        "Equal objects with unequal hash codes should cause an exception.",
        new EqualsTester().newEqualityGroup(
            new DefinedHashCodeObject(1), new DefinedHashCodeObject(2)));
  }

  /**
   * Check 6) comparing each pair of objects from different equality groups
   * returns false
   */
  public void testEquals_equalObjectsInDifferentEqualityGroups() {
    assertTestEqualsCausesException(
        "Equal objects in different equality group should cause an exception.",
        new EqualsTester().newEqualityGroup("a").newEqualityGroup("a"));
  }
}
