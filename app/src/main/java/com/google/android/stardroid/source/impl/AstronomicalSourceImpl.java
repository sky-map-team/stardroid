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

package com.google.android.stardroid.source.impl;

import java.util.ArrayList;

import com.google.android.stardroid.source.ImagePrimitive;
import com.google.android.stardroid.source.LinePrimitive;
import com.google.android.stardroid.source.PointPrimitive;
import com.google.android.stardroid.source.TextPrimitive;

/**
 * Simple class for implementing the AstronomicalSource interface. We may merge
 * the two in the future (but for now, this lets us do some parallel
 * development).
 *
 *
 * @author Brent Bryan
 */
public class AstronomicalSourceImpl {
  private float level;
  private ArrayList<String> names;

  private ArrayList<ImagePrimitive> imagePrimitives;
  private ArrayList<LinePrimitive> linePrimitives;
  private ArrayList<PointPrimitive> pointPrimitives;
  private ArrayList<TextPrimitive> textPrimitives;

  public ArrayList<String> getNames() {
    return names;
  }

  public void setNames(ArrayList<String> names) {
    this.names = names;
  }

  public float getLevel() {
    return level;
  }

  public void setLevel(float level) {
    this.level = level;
  }

  public ArrayList<ImagePrimitive> getImagePrimitives() {
    return imagePrimitives;
  }

  public void setImagePrimitives(ArrayList<ImagePrimitive> imagePrimitives) {
    this.imagePrimitives = imagePrimitives;
  }

  public ArrayList<LinePrimitive> getLineSources() {
    return linePrimitives;
  }

  public void setLineSources(ArrayList<LinePrimitive> linePrimitives) {
    this.linePrimitives = linePrimitives;
  }

  public ArrayList<PointPrimitive> getPointSources() {
    return pointPrimitives;
  }

  public void setPointSources(ArrayList<PointPrimitive> pointPrimitives) {
    this.pointPrimitives = pointPrimitives;
  }

  public ArrayList<TextPrimitive> getTextSources() {
    return textPrimitives;
  }

  public void setTextSources(ArrayList<TextPrimitive> textPrimitives) {
    this.textPrimitives = textPrimitives;
  }

  public void addPoint(PointPrimitive point) {
    if (point == null) {
      pointPrimitives = new ArrayList<PointPrimitive>();
    }
    pointPrimitives.add(point);
  }

  public void addLabel(TextPrimitive label) {
    if (label == null) {
      textPrimitives = new ArrayList<TextPrimitive>();
    }
    textPrimitives.add(label);
  }

  public void addImage(ImagePrimitive image) {
    if (image == null) {
      imagePrimitives = new ArrayList<ImagePrimitive>();
    }
    imagePrimitives.add(image);
  }

  public void addLine(LinePrimitive line) {
    if (line == null) {
      linePrimitives = new ArrayList<LinePrimitive>();
    }
    linePrimitives.add(line);
  }
}
