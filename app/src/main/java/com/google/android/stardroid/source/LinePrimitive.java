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

package com.google.android.stardroid.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.graphics.Color;

import com.google.android.stardroid.math.RaDec;
import com.google.android.stardroid.math.Vector3;

/**
 * For representing constellations, constellation boundaries etc.
 */
public class LinePrimitive extends AbstractPrimitive {

  public final List<Vector3> vertices;
  public final List<RaDec> raDecs;
  public final float lineWidth;

  public LinePrimitive() {
    this(Color.WHITE, new ArrayList<Vector3>(), 1.5f);
  }

  public LinePrimitive(int color) {
    this(color, new ArrayList<Vector3>(), 1.5f);
  }

  public LinePrimitive(int color, List<Vector3> vertices, float lineWidth) {
    super(color);

    this.vertices = vertices;
    this.raDecs = new ArrayList<RaDec>();
    this.lineWidth = lineWidth;
  }

  public float getLineWidth() {
    return lineWidth;
  }
  public List<Vector3> getVertices() {
    List<Vector3> result;
    if (vertices != null) {
      result = vertices;
    } else {
      result = new ArrayList<Vector3>();
    }
    return Collections.unmodifiableList(result);
  }
}
