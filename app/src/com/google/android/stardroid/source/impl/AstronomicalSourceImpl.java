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

import com.google.android.stardroid.source.ImageSource;
import com.google.android.stardroid.source.LineSource;
import com.google.android.stardroid.source.PointSource;
import com.google.android.stardroid.source.TextSource;

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

  private ArrayList<ImageSource> imageSources;
  private ArrayList<LineSource> lineSources;
  private ArrayList<PointSource> pointSources;
  private ArrayList<TextSource> textSources;

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

  public ArrayList<ImageSource> getImageSources() {
    return imageSources;
  }

  public void setImageSources(ArrayList<ImageSource> imageSources) {
    this.imageSources = imageSources;
  }

  public ArrayList<LineSource> getLineSources() {
    return lineSources;
  }

  public void setLineSources(ArrayList<LineSource> lineSources) {
    this.lineSources = lineSources;
  }

  public ArrayList<PointSource> getPointSources() {
    return pointSources;
  }

  public void setPointSources(ArrayList<PointSource> pointSources) {
    this.pointSources = pointSources;
  }

  public ArrayList<TextSource> getTextSources() {
    return textSources;
  }

  public void setTextSources(ArrayList<TextSource> textSources) {
    this.textSources = textSources;
  }

  public void addPoint(PointSource point) {
    if (point == null) {
      pointSources = new ArrayList<PointSource>();
    }
    pointSources.add(point);
  }

  public void addLabel(TextSource label) {
    if (label == null) {
      textSources = new ArrayList<TextSource>();
    }
    textSources.add(label);
  }

  public void addImage(ImageSource image) {
    if (image == null) {
      imageSources = new ArrayList<ImageSource>();
    }
    imageSources.add(image);
  }

  public void addLine(LineSource line) {
    if (line == null) {
      lineSources = new ArrayList<LineSource>();
    }
    lineSources.add(line);
  }
}
