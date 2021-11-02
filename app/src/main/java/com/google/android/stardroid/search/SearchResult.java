// Copyright 2008 Google Inc.
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

package com.google.android.stardroid.search;

import com.google.android.stardroid.math.Vector3;

/**
 * A single search result.
 *
 * @author John Taylor
 */
public class SearchResult {
  /** The coordinates of the object.*/
  public Vector3 coords;
  /** The user-presentable name of the object, properly capitalized.*/
  public String capitalizedName;

  /**
   * @param capitalizedName The user-presentable name of the object, properly capitalized.
   * @param coords The geocentric coordinates of the object.
   */
  public SearchResult(String capitalizedName, Vector3 coords) {
    this.capitalizedName = capitalizedName;
    this.coords = coords;
  }

  @Override
  public String toString() {
    return capitalizedName;
  }
}
