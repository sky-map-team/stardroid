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

import java.util.HashMap;

import com.google.android.stardroid.base.Maps;

import junit.framework.TestCase;

/**
 * Unit tests for the Maps class.
 * 
 * @author Brent Bryan
 */
public class MapsTest extends TestCase {

  public void testNewHashMap() {
    HashMap<String, Integer> map = Maps.newHashMap();
    assertEquals(0, map.size());
    
    // Ensure it the map is modifiable 
    map.put("one", 1);
    assertEquals(1, map.size());
  }
}
