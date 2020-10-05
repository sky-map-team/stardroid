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

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class tests the following:
 * <ol>
 * <li>comparing each object against itself returns true
 * <li>comparing each object against null returns false
 * <li>comparing each object an instance of an incompatible class returns false
 * <li>comparing each pair of objects within the same equality group returns
 *     true
 * <li>the hash code of any two equal objects are equal
 * <li>comparing each pair of objects from different equality groups returns
 *     false
 * </ol>
 *
 *  This class is a simple stopgap until Guava includes a more feature rich
 *  EqualsTester as part of it's distribution.
 *
 *  // TODO(brent): Switch to Guava's version when available.
 *
 * @author Brent Bryan
 */
public class EqualsTester {
  List<List<Object>> groups = new ArrayList<List<Object>>();

  public EqualsTester newEqualityGroup(Object... objs) {
    groups.add(Arrays.asList(objs));
    return this;
  }

  public void testEquals() {
    // Test the first 5 conditions:
    for (List<Object> group : groups) {
      for (Object obj1 : group) {
        Assert.assertEquals(obj1 + " should be equal to itself", obj1, obj1);
        assertNotEqual(obj1, null);
        assertNotEqual(obj1, IncompatibleObject.INSTANCE);
        for (Object obj2 : group) {
          if (obj2 != obj1) {
            Assert.assertEquals(obj2 + " should equal " + obj1, obj1, obj2);
            Assert.assertEquals(obj2 + " should have the same hashcode as " + obj1,
                obj1.hashCode(), obj2.hashCode());
          }
        }
      }
    }

    // Test the last condition:
    for (List<Object> group1 : groups) {
      for (List<Object> group2 : groups) {
        if (group1 == group2) {
          continue;
        }

        for (Object obj1 : group1) {
          for (Object obj2 : group2) {
            assertNotEqual(obj1, obj2);
          }
        }
      }
    }
  }

  private void assertNotEqual(Object obj1, Object obj2) {
    Assert.assertFalse(obj1 + " should not equal " + obj2, obj1.equals(obj2));
  }

  private static final class IncompatibleObject {
    public static final IncompatibleObject INSTANCE = new IncompatibleObject();

    // No external instances.
    private IncompatibleObject() {}

    @Override
    public String toString() {
      return "Incompatiable Object";
    }
  }
}
