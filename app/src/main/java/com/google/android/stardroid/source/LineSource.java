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

package com.google.android.stardroid.source;

import java.util.List;

import com.google.android.stardroid.units.GeocentricCoordinates;

/**
 * This interface corresponds to a set of successive line segments (drawn from
 * consecutive vertices). That is, for the vertices {A, B, C, D}, lines should
 * be drawn between A and B, B and C, and C and D.
 * 
 * @author Brent Bryan
 */
public interface LineSource extends Colorable {

  /**
   * Returns the width of the line to be drawn.
   */
  float getLineWidth();

  // TODO(brent): Discuss with James to add solid, dashed, dotted, etc.
  
  /**
   * Returns an ordered list of the vertices which should be used to draw a
   * polyline in the renderer.
   */
  List<GeocentricCoordinates> getVertices();
}
