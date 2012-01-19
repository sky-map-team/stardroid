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
package com.google.android.stardroid.test.util;

import com.google.android.stardroid.base.Lists;
import com.google.android.stardroid.test.util.ImmutableEqualsTester.TestableBuilder;

import java.util.Arrays;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

/**
 * Unit tests for the {@link ImmutableEqualsTester} class.
 *
 * @author Brent Bryan
 */
public class ImmutableEqualsTesterTest extends TestCase {
  /**
   * A test object with trivial semantics that we'll use to test the
   * {@link ImmutableEqualsTester}.
   */
  public static class MyObject {
    private final int id;
    private final String name;

    public MyObject(int id, Foo foo) {
      this(id, "" + foo);
    }

    public MyObject(int id, SubFoo subFoo) {
      this(id, "" + subFoo);
    }

    public MyObject(int id, String name) {
      this.id = id;
      this.name = name;
    }

    public MyObject(int id, String... strings) {
      this(id, Arrays.asList(strings).toString());
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof MyObject) {
        MyObject that = (MyObject) o;
        return this.id == that.id && ((this.name == null && that.name == null)
            || this.name != null && this.name.equals(that.name));
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(new Object[] {id, name});
    }

    @Override
    public String toString() {
      return String.format("Id: %d   Name: %s", id, name);
    }
  }

  /** A marker class used for testing. */
  static class Foo {
    @Override
    public String toString() {
      return "foo" + hashCode();
    }
  }

  /** A marker class which is a subclass of {@link Foo} used for testing. */
  static class SubFoo extends Foo {
    @Override
    public String toString() {
      return "subfoo" + hashCode();
    }
  }

  public void testIsArgOfType() {
    assertTrue(TestableBuilder.isArgOfType((byte)2, Byte.TYPE));
    assertTrue(TestableBuilder.isArgOfType((short)2, Short.TYPE));
    assertTrue(TestableBuilder.isArgOfType(4, Integer.TYPE));
    assertTrue(TestableBuilder.isArgOfType(Integer.valueOf(4), Integer.TYPE));
    assertTrue(TestableBuilder.isArgOfType(4L, Long.TYPE));
    assertTrue(TestableBuilder.isArgOfType(Long.valueOf(4L), Long.TYPE));
    assertTrue(TestableBuilder.isArgOfType(4.0f, Float.TYPE));
    assertTrue(TestableBuilder.isArgOfType(4.0, Double.TYPE));
    assertTrue(TestableBuilder.isArgOfType(true, Boolean.TYPE));
    assertTrue(TestableBuilder.isArgOfType('a', Character.TYPE));
    assertTrue(TestableBuilder.isArgOfType("Hello", String.class));
    assertTrue(TestableBuilder.isArgOfType(new Foo(), Foo.class));
    assertTrue(TestableBuilder.isArgOfType(new SubFoo(), Foo.class));
    assertTrue(TestableBuilder.isArgOfType(null, String.class));
    assertTrue(TestableBuilder.isArgOfType(null, Foo.class));
    assertTrue(TestableBuilder.isArgOfType(null, SubFoo.class));

    assertFalse(TestableBuilder.isArgOfType(new Foo(), SubFoo.class));
    assertFalse(TestableBuilder.isArgOfType(new Foo(), String.class));
    assertFalse(TestableBuilder.isArgOfType("Hi", Foo.class));
    assertFalse(TestableBuilder.isArgOfType(null, Long.TYPE));
  }

  public void testGetArgs() {
    Object[] defaultArgs = {"a", "b", "c", "d"};
    Object[] altArgs = {"1", "2", "3", "4"};

    // Nasty array to List conversion is required as arrays equality is pointer
    // based, not equality based.
    assertEquals(Lists.asList("1", "b", "c", "d"),
        Arrays.asList(TestableBuilder.getArgs(defaultArgs, altArgs, 0)));
    assertEquals(Lists.asList("a", "2", "c", "d"),
        Arrays.asList(TestableBuilder.getArgs(defaultArgs, altArgs, 1)));
    assertEquals(Lists.asList("a", "b", "c", "4"),
        Arrays.asList(TestableBuilder.getArgs(defaultArgs, altArgs, 3)));
  }

  public void testGetArgs_indexOutOfBounds() {
    Object[] defaultArgs = {"a", "b", "c", "d"};
    Object[] altArgs = {"1", "2", "3", "4"};

    assertEquals(defaultArgs, TestableBuilder.getArgs(defaultArgs, altArgs, -1));
    assertEquals(defaultArgs, TestableBuilder.getArgs(defaultArgs, altArgs, defaultArgs.length));
    assertEquals(defaultArgs,
        TestableBuilder.getArgs(defaultArgs, altArgs, defaultArgs.length + 10));
  }

  public void testEquals() {
    ImmutableEqualsTester
        .of(MyObject.class)
        .defaultArgs(1, "Pittsburgh")
        .alternativeArgs(2, "Seattle")
        .testEquals();
  }

  public void testEquals_nullValues() {
    ImmutableEqualsTester
        .of(MyObject.class)
        .defaultArgs(1, null)
        .alternativeArgs(Integer.valueOf(2), "Seattle")
        .testEquals();
  }

  public void testEquals_findUniqueConstructor() {
    ImmutableEqualsTester
        .of(MyObject.class)
        .defaultArgs(1, null)
        .alternativeArgs(2, new Foo())
        .testEquals();
  }

  public void testEquals_findVarArgsConstructor() {
    ImmutableEqualsTester
        .of(MyObject.class)
        .defaultArgs(1, new String[] {"a", "b", "c"})
        .alternativeArgs(2, new String[] {"1", "2", "3"})
        .testEquals();
  }

  private <F> void assertTestEqualsCausesException(String message, TestableBuilder<F> builder) {
    boolean caughtError = false;
    try {
      builder.testEquals();
    } catch (AssertionFailedError e) {
      caughtError = true;
    }

    if (!caughtError) {
      fail(message);
    }
  }

  public void testEquals_defaultArgsConstructorTypesMismatch() {
    assertTestEqualsCausesException(
        "Mismatchs between default argument types and constructor types should cause an error.",
        ImmutableEqualsTester
            .of(MyObject.class)
            .defaultArgs(1, new Foo())
            .alternativeArgs(2, "Seattle"));
  }

  public void testEquals_alternateArgsConstructorTypesMismatch() {
    assertTestEqualsCausesException(
        "Mismatchs between alternative argument types and constructor types should cause an error.",
        ImmutableEqualsTester
            .of(MyObject.class)
            .defaultArgs(1, "Pittsburgh")
            .alternativeArgs(2, new SubFoo()));
  }

  public void testEquals_noConstructorForTypes() {
    assertTestEqualsCausesException(
        "Lack of a constructor which matches the argument types should cause an error.",
        ImmutableEqualsTester
            .of(MyObject.class)
            .defaultArgs(1, "Pittsburgh", new Foo())
            .alternativeArgs(2, "Seattle", new SubFoo()));
  }

  public void testEquals_multipleConstructors() {
    assertTestEqualsCausesException(
        "Multiple constructors which match the arguments should cause an error.",
        ImmutableEqualsTester
            .of(MyObject.class)
            .defaultArgs(1, null)
            .alternativeArgs(2, new SubFoo()));
  }
}
