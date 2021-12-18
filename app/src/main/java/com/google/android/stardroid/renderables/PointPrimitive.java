// Copyright 2008 Google Inc.
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

package com.google.android.stardroid.renderables;

import com.google.android.stardroid.math.CoordinateManipulationsKt;
import com.google.android.stardroid.math.Vector3;

/**
 * This class represents a astronomical point source, such as a star, or a distant galaxy.
 *
 * @author Brent Bryan
 */

public class PointPrimitive extends AbstractPrimitive {
  public final int size;
  private final Shape pointShape;

  public PointPrimitive(float ra, float dec, int color, int size) {
    this(CoordinateManipulationsKt.getGeocentricCoords(ra, dec), color, size);
  }

  public PointPrimitive(Vector3 coords, int color, int size) {
    this(coords, color, size, Shape.CIRCLE);
  }

  public PointPrimitive(Vector3 coords, int color, int size, Shape pointShape) {
    super(coords, color);
    this.size = size;
    this.pointShape = pointShape;
  }

  public int getSize() {
    return size;
  }

  public Shape getPointShape() {
    return pointShape;
  }

  public enum Shape {
    CIRCLE(0),
    STAR(1),
    ELLIPTICAL_GALAXY(2),
    SPIRAL_GALAXY(3),
    IRREGULAR_GALAXY(4),
    LENTICULAR_GALAXY(3),
    GLOBULAR_CLUSTER(5),
    OPEN_CLUSTER(6),
    NEBULA(7),
    HUBBLE_DEEP_FIELD(8);

    private final int imageIndex;

    Shape(int imageIndex) {
      this.imageIndex = imageIndex;
    }

    public int getImageIndex() {
      // return imageIndex;
      return 0;
    }
  }
}
