// Copyright 2009 Google Inc.
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

package com.google.android.stardroid.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class AllTests extends TestCase {

  public static Test suite() {
    TestSuite suite = new TestSuite("com.google.android.stardroid.test");

    suite.addTest(com.google.android.stardroid.test.base.AllTests.suite());
    suite.addTest(com.google.android.stardroid.test.control.AllTests.suite());
    //suite.addTest(com.google.android.stardroid.test.search.AllTests.suite());
    //suite.addTest(com.google.android.stardroid.test.units.AllTests.suite());
    //suite.addTest(com.google.android.stardroid.test.util.AllTests.suite());

    return suite;
  }
}
