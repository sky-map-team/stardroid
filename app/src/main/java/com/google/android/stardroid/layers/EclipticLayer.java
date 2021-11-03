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

package com.google.android.stardroid.layers;

import android.content.res.Resources;
import android.graphics.Color;

import com.google.android.stardroid.R;
import com.google.android.stardroid.source.AbstractAstronomicalSource;
import com.google.android.stardroid.source.AstronomicalSource;
import com.google.android.stardroid.source.LinePrimitive;
import com.google.android.stardroid.source.TextPrimitive;
import com.google.android.stardroid.math.CoordinateManipulationsKt;
import com.google.android.stardroid.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a Layer for the Ecliptic.
 *
 * @author John Taylor
 * @author Brent Bryan
 */

public class EclipticLayer extends AbstractSourceLayer {
  public EclipticLayer(Resources resources) {
    super(resources, false);
  }

  @Override
  protected void initializeAstroSources(ArrayList<AstronomicalSource> sources) {
    sources.add(new EclipticSource(getResources()));
  }

  @Override
  public int getLayerDepthOrder() {
    return 50;
  }

  @Override
  protected int getLayerNameId() {
    return R.string.show_grid_pref;
  }

  @Override
  public String getPreferenceId() {
    return "source_provider.4";
  }

  /** Implementation of {@link AstronomicalSource} for the ecliptic source. */
  private static class EclipticSource extends AbstractAstronomicalSource {
    // Earth's Angular Tilt
    private static final float EPSILON = 23.439281f;
    private static final int LINE_COLOR = Color.argb(20, 248, 239, 188);

    private ArrayList<LinePrimitive> linePrimitives = new ArrayList<LinePrimitive>();
    private ArrayList<TextPrimitive> textPrimitives = new ArrayList<TextPrimitive>();

    public EclipticSource(Resources res) {
      String title = res.getString(R.string.ecliptic);
      textPrimitives.add(new TextPrimitive(90.0f, EPSILON, title, LINE_COLOR));
      textPrimitives.add(new TextPrimitive(270f, -EPSILON, title, LINE_COLOR));

      // Create line source.
      float[] ra = {0, 90, 180, 270, 0};
      float[] dec = {0, EPSILON, 0, -EPSILON, 0};

      ArrayList<Vector3> vertices = new ArrayList<Vector3>();
      for (int i = 0; i < ra.length; ++i) {
        vertices.add(CoordinateManipulationsKt.getGeocentricCoords(ra[i], dec[i]));
      }
      linePrimitives.add(new LinePrimitive(LINE_COLOR, vertices, 1.5f));
    }

    @Override
    public List<TextPrimitive> getLabels() {
      return textPrimitives;
    }

    @Override
    public List<LinePrimitive> getLines() {
      return linePrimitives;
    }
  }
}
