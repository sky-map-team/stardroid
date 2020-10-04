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

import com.google.android.stardroid.base.Provider;
import com.google.android.stardroid.base.VisibleForTesting;

import org.junit.Assert;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Version of the {@link EqualsTester} class for testing immutable classes.
 * With this class, you need only specify a default and an alternative set of
 * arguments for each parameter in the object's constructor. This class then
 * automatically finds the correct constructor for the object to be tested and
 * constructs objects of type E for each combination of arguments (where the
 * arguments one-by-one set to the alternative values) and tests to ensure that
 * objects of type E constructed with the same arguments are equal, but objects
 * constructed with unequal arguments are not equal. Using this class, we can
 * condense the boiler plate of:
 *
 * <code><pre>
 * new EqualsTester()
 *     .addEqualityGroup(new Foo(1, "a"), new Foo(1, "a"))
 *     .addEqualityGroup(new Foo(2, "a"), new Foo(2, "a"))
 *     .addEqualityGroup(new Foo(1, "b"), new Foo(1, "b))
 *     .testEquals();
 * </pre></code>
 *
 * to
 *
 * <code><pre>
 * ImmutableEqualsTester
 *   .of(Foo.class)
 *   .defaultArgs(1, "a")
 *   .alternativeArgs(2, "b")
 *   .testEquals();
 * </pre></code>
 *
 * In addition to condensing the code required to do equals testing on immutable
 * objects, this class reduces the risk of typos caused by excessive cut and
 * pasting.
 *
 * @param <E> type of the object being tested for equality
 *
 * @author Brent Bryan
 */
public class ImmutableEqualsTester<E> {
  private final Class<E> clazz;

  public ImmutableEqualsTester(Class<E> clazz) {
    this.clazz = clazz;
  }

  /**
   * Sets the default arguments used to construct new instances of objects of
   * type E.
   */
  public AlternativeBuilder<E> defaultArgs(Object... defaultArgs) {
    return new AlternativeBuilder<E>(clazz, defaultArgs);
  }

  /**
   * Convenience constructor for {@link ImmutableEqualsTester}s so that the type
   * doesn't need to be repeated upon construction.
   */
  public static <F> ImmutableEqualsTester<F> of(Class<F> clazz) {
    return new ImmutableEqualsTester<F>(clazz);
  }

  /**
   * EDSL class for the step where we need to set the alternative arguments for
   * the equality test.
   *
   * @param <F> type of object begin tested for equality
   */
  public static class AlternativeBuilder<F> {
    private final Class<F> clazz;
    private final Object[] defaultArgs;

    private AlternativeBuilder(Class<F> clazz, Object[] defaultArgs) {
      this.clazz = clazz;
      this.defaultArgs = defaultArgs;
    }

    /**
     * Sets the alternative arguments used to construct new instances of objects
     * of type E.
     */
    public TestableBuilder<F> alternativeArgs(Object... args) {
      return new TestableBuilder<F>(clazz, defaultArgs, args);
    }
  }

  /**
   * EDSL class for the step where we perform the equality test.
   *
   * @param <F> type of object begin tested for equality
   */
  public static class TestableBuilder<F> {
    /** Map between primitive types and their object class counterparts */
    private static final Map<Class<?>, Class<?>> primitiveTypeMap =
        new HashMap<Class<?>, Class<?>>();

    static {
      primitiveTypeMap.put(Byte.TYPE, Byte.class);
      primitiveTypeMap.put(Short.TYPE, Short.class);
      primitiveTypeMap.put(Integer.TYPE, Integer.class);
      primitiveTypeMap.put(Long.TYPE, Long.class);
      primitiveTypeMap.put(Float.TYPE, Float.class);
      primitiveTypeMap.put(Double.TYPE, Double.class);
      primitiveTypeMap.put(Boolean.TYPE, Boolean.class);
      primitiveTypeMap.put(Character.TYPE, Character.class);
    }

    private final Class<F> clazz;
    private final Object[] defaultArgs;
    private final Object[] alternateArgs;
    private Provider<EqualsTester> equalsTesterProvider;

    private TestableBuilder(Class<F> clazz, Object[] defaultArgs, Object[] alternativeArgs) {
      this.clazz = clazz;
      this.defaultArgs = defaultArgs;
      this.alternateArgs = alternativeArgs;
      this.equalsTesterProvider = new Provider<EqualsTester>() {
        @Override
        public EqualsTester get() {
          return new EqualsTester();
        }
      };
    }

    /**
     * Sets the {@link Provider} of {@link EqualsTester}s used in the
     * {@link #testEquals} method to the given object. Used during testing to
     * mock out the {@link EqualsTester} dependency.
     */
    @VisibleForTesting
    TestableBuilder<F> setEqualsTesterProvider(Provider<EqualsTester> provider) {
      this.equalsTesterProvider = provider;
      return this;
    }

    /**
     * Returns true if the given argument {@link Class} is of the same type as
     * the given type {@link Class}. Typically, this means that the function
     * returns true if the argument {@link Class} is a subclass of the type
     * {@link Class}. However, special magic is required to deal with primitive
     * types.
     */
    @VisibleForTesting
    static boolean isArgOfType(Object arg, Class<?> type) {
      if (arg == null) {
        return Object.class.isAssignableFrom(type);
      }

      Class<?> autoBoxedType = primitiveTypeMap.get(type);
      if (autoBoxedType != null) {
        return autoBoxedType.isInstance(arg);
      }
      return type.isInstance(arg);
    }

    /**
     * Find all those constructors which match the types of the default
     * arguments.
     */
    @VisibleForTesting
    static boolean areArgsCompatiableWithTypes(Object[] args, Class<?>[] types) {
      if (args.length != types.length) {
        return false;
      }

      for (int i = 0; i < args.length; i++) {
        if (!isArgOfType(args[i], types[i])) {
          return false;
        }
      }
      return true;
    }

    /**
     * Returns a {@link List} of {@link Constructor}s which are compatible with
     * the given set of arguments. More than one constructor may be compatible
     * due to overloading.
     */
    static <T> List<Constructor<T>> getCompatibleConstructors(
        List<Constructor<T>> constructors, Object[] args) {
      List<Constructor<T>> result = new ArrayList<Constructor<T>>();
      for (Constructor<T> constructor : constructors) {
        if (constructor.getModifiers() == Modifier.PRIVATE) {
          continue;
        }

        if (areArgsCompatiableWithTypes(args, constructor.getParameterTypes())) {
          result.add(constructor);
        }
      }
      return result;
    }

    /**
     * Creates an array of arguments where each value in the array is equal to
     * the value in the defaultArgs array, except for the one at the given index
     * value, which is equal to the value in the alternativeArgs array for that
     * index. If index is out of the default args bounds, then this method
     * returns the defaultArgs array.
     */
    @VisibleForTesting
    static Object[] getArgs(Object[] defaultArgs, Object[] alternateArgs, int index) {
      if (index < 0 || index >= defaultArgs.length) {
        return defaultArgs;
      }

      Object[] args = new Object[defaultArgs.length];
      System.arraycopy(defaultArgs, 0, args, 0, defaultArgs.length);
      args[index] = alternateArgs[index];
      return args;
    }

    /**
     * Constructs and tests that the N + 1 possible instances (where each of the
     * N parameters are set to the alternative parameter one at a time) of
     * objects of type F are equal to other instances created with the same
     * arguments, but unequal to objects of the same type created with different
     * arguments.
     */
    public void testEquals() {
      @SuppressWarnings("unchecked")
      List<Constructor<F>> constructors = (List) Arrays.asList(clazz.getDeclaredConstructors());
      constructors = getCompatibleConstructors(constructors, defaultArgs);
      Assert.assertFalse(String.format(
          "Expected at least one constructor to match default args (%s), but found none.",
          Arrays.asList(defaultArgs)),
          constructors.isEmpty());

      constructors = getCompatibleConstructors(constructors, alternateArgs);
      Assert.assertEquals(String.format(
          "Expected only one constructor to match default and alternative args, but found %d.",
          constructors.size()), 1, constructors.size());

      Constructor<F> constructor = constructors.get(0);
      try {
        EqualsTester tester = equalsTesterProvider.get();
        for (int i = 0; i <= alternateArgs.length; i++) {
          Object[] args = getArgs(defaultArgs, alternateArgs, i);
          tester.newEqualityGroup(constructor.newInstance(args), constructor.newInstance(args));
        }
        tester.testEquals();

      } catch (SecurityException e) {
        Assert.fail("Could not access constructor for " + clazz + " with types: "
            + Arrays.asList(constructor.getParameterTypes()));
      } catch (InstantiationException e) {
        Assert.fail("Failed to create a new " + clazz + " instance: " + e);
      } catch (IllegalAccessException e) {
        Assert.fail("Failed to create a new " + clazz + " instance: " + e);
      } catch (InvocationTargetException e) {
        Assert.fail("Failed to create a new " + clazz + " instance: " + e);
      }
    }
  }
}
