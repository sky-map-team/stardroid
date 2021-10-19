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

package com.google.android.stardroid.math;

public class VectorUtil {

  public static float dotProduct(Vector3 p1, Vector3 p2) {
    return p1.x * p2.x + p1.y * p2.y + p1.z * p2.z;
  }
  
  public static Vector3 crossProduct(Vector3 p1, Vector3 p2) {
    return new Vector3(p1.y * p2.z - p1.z * p2.y,
                      -p1.x * p2.z + p1.z * p2.x,
                       p1.x * p2.y - p1.y * p2.x);
  }

  public static Vector3 sum(Vector3 v1, Vector3 v2) {
    return new Vector3(v1.x + v2.x, v1.y + v2.y, v1.z + v2.z);
  }
  
  public static Vector3 difference(Vector3 v1, Vector3 v2) {
    return sum(v1, v2.negateCopy());
  }
}
