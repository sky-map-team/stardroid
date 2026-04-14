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
import java.util.EnumSet;
import java.util.List;

/**
 * Unittests for the Lists class.
 * 
 * @author Brent Bryan
 */
public class ListsTest extends TestCase {
  private enum TestEnum {
    ONE, TWO, THREE
  }

  public void testTransform() {
    EnumSet<TestEnum> set = EnumSet.of(TestEnum.TWO, TestEnum.ONE);
    List<Integer> list = Lists.transform(set, new Transform<TestEnum, Integer>() {
      public Integer transform(TestEnum e) {
        switch (e) {
          case ONE:
            return 1;
          case TWO:
            return 2;
          case THREE:
            return 3;
        }
        throw new RuntimeException();
      }
    });

    assertEquals(2, list.size());
    assertEquals(1, list.get(0).intValue());
    assertEquals(2, list.get(1).intValue());
  }

  public void testAsList_fromNonList() {
    EnumSet<TestEnum> set = EnumSet.of(TestEnum.TWO, TestEnum.ONE);
    List<TestEnum> list = Lists.asList(set);

    assertEquals(2, list.size());
    assertEquals(TestEnum.ONE, list.get(0));
    assertEquals(TestEnum.TWO, list.get(1));
  }

  public void testAsList_fromList() {
    List<Integer> startList = Arrays.asList(new Integer[] {2, 4, 1});
    List<Integer> newList = Lists.asList(startList);

    assertEquals(3, newList.size());
    assertEquals(2, newList.get(0).intValue());
    assertEquals(4, newList.get(1).intValue());
    assertEquals(1, newList.get(2).intValue());
    assertTrue(startList == newList);
  }
}
