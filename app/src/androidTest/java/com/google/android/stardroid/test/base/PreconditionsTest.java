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

package com.google.android.stardroid.test.base;

import com.google.android.stardroid.base.Lists;
import com.google.android.stardroid.base.PreconditionException;
import com.google.android.stardroid.base.Preconditions;

import junit.framework.TestCase;

/**
 * Unit tests for the Preconditions checks.
 * 
 * @author Brent Bryan
 */
public class PreconditionsTest extends TestCase {

  public void testCheck() {
    Preconditions.check(true);
    Preconditions.check(true, "foo");

    try {
      Preconditions.check(false);
      fail("check false should throw an exception");
    } catch (PreconditionException e) {
      // check false should throw an exception.
    }

    String message = "foo";
    try {
      Preconditions.check(false, message);
      fail("check false should throw an exception");
    } catch (PreconditionException e) {
      // check false should throw an exception.
      assertEquals(message, e.getMessage());
    }
  }

  public void testCheckNotNull() {
    Preconditions.checkNotNull(this);

    try {
      Preconditions.checkNotNull(null);
      fail("check not null should throw an exception when given null");
    } catch (PreconditionException e) {
      // check not null should throw an exception when given null
    }
  }

  public void testCheckNotEmpty() {
    Preconditions.checkNotEmpty("foo");

    String[] testStrings = {null, "", "  ", "\t"};
    for (String input : testStrings) {
      try {
        Preconditions.checkNotEmpty(input);
        fail("check not empty should throw an exception when given :" + input + ":");
      } catch (PreconditionException e) {
        // check not empty should throw an exception when given bad input
      }
    }
  }

  public void testCheckEqual() {
    Preconditions.checkEqual(null, null);
    // Check ==
    Preconditions.checkEqual("foo", "foo");
    // Check .equals
    Preconditions.checkEqual(Lists.emptyList(), Lists.emptyList());

    String[] testInputOne = {null, "foo", "bar"};
    String[] testInputTwo = {"foo", null, "foo"};

    for (int i = 0; i < testInputOne.length; i++) {
      String s1 = testInputOne[i];
      String s2 = testInputTwo[i];
      try {
        Preconditions.checkEqual(s1, s2);
        fail("check equals should fail if entries are not equal");
      } catch (PreconditionException e) {
        // check equals should fail if entries are not equal
      }
    }
  }

  public void testCheckNotEqual() {
    Preconditions.checkNotEqual(null, "foo");
    Preconditions.checkNotEqual("foo", null);
    Preconditions.checkNotEqual("foo", "bar");

    try {
      String s = "foo";
      Preconditions.checkNotEqual(s, s);
      fail("check not equals should fail if entries are equal");
    } catch (PreconditionException e) {
      // check not equals should fail if entries are equal
    }

    try {
      Preconditions.checkNotEqual(Lists.emptyList(), Lists.emptyList());
      fail("check not equals should fail if entries are equal");
    } catch (PreconditionException e) {
      // check not equals should fail if entries are equal
    }
  }

  public void testCheckBetween() {
    Preconditions.checkBetween(3, 2, 4);
    Preconditions.checkBetween(2, 2, 4);
    Preconditions.checkBetween(4, 2, 4);

    try {
      Preconditions.checkBetween(1, 2, 4);
      fail("check between should fail if the value is not between then given endpoints.");
    } catch (PreconditionException e) {
      // check between should fail if the value is not between then given
      // endpoints
    }

    try {
      Preconditions.checkBetween(5, 2, 4);
      fail("check between should fail if the value is not between then given endpoints.");
    } catch (PreconditionException e) {
      // check between should fail if the value is not between then given
      // endpoints
    }
  }
}
