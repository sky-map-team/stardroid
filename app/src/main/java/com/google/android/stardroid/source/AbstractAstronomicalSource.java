// Copyright 2010 Google Inc.
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

import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType;
import com.google.android.stardroid.math.Vector3;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Base implementation of the {@link AstronomicalSource} and {@link Sources}
 * interfaces.
 *
 * @author Brent Bryan
 */
public abstract class AbstractAstronomicalSource implements AstronomicalSource, Sources {

  @Override
  public Sources initialize() {
    return this;
  }

  @Override
  public EnumSet<UpdateType> update() {
    return EnumSet.noneOf(UpdateType.class);
  }

  /** Implementors of this method must implement {@link #getSearchLocation}. */
  @Override
  public List<String> getNames() {
    return Collections.emptyList();
  }

  @Override
  public Vector3 getSearchLocation() {
    throw new UnsupportedOperationException("Should not be called");
  }

  @Override
  public List<ImagePrimitive> getImages() {
    return Collections.emptyList();
  }

  @Override
  public List<TextPrimitive> getLabels() {
    return Collections.emptyList();
  }

  @Override
  public List<LinePrimitive> getLines() {
    return Collections.emptyList();
  }

  @Override
  public List<PointPrimitive> getPoints() {
    return Collections.emptyList();
  }
}
