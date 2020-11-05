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

package com.google.android.stardroid.units;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class LatLong2Test {
  private static final float TOL = 1e-4f;

  @Test
  public void testDistanceFrom90Degrees() {
    LatLong l1 = new LatLong(0, 0);
    LatLong l2 = new LatLong(0, 90);
    assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(90f);
  }

  @Test
  public void testDistanceFromSame() {
    LatLong l1 = new LatLong(30, 9);
    LatLong l2 = new LatLong(30, 9);
    assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(0f);
  }

  @Test
  public void testDistanceFromOppositePoles() {
    LatLong l1 = new LatLong(-90, 45);
    LatLong l2 = new LatLong(90, 45);
    assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(180f);
  }

  @Test
  public void testDistanceFromOnEquator() {
    LatLong l1 = new LatLong(0, -20);
    LatLong l2 = new LatLong(0, 30);
    assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(50f);
  }

  @Test
  public void testDistanceFromOnMeridian() {
    LatLong l1 = new LatLong(-10, 0);
    LatLong l2 = new LatLong(40, 0);
    assertThat(l1.distanceFrom(l2)).isWithin(TOL).of(50f);
  }
}
