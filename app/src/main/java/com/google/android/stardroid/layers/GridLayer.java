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

package com.google.android.stardroid.layers;

import com.google.android.stardroid.R;
import com.google.android.stardroid.source.AbstractAstronomicalSource;
import com.google.android.stardroid.source.AstronomicalSource;
import com.google.android.stardroid.source.LineSource;
import com.google.android.stardroid.source.TextSource;
import com.google.android.stardroid.source.impl.LineSourceImpl;
import com.google.android.stardroid.source.impl.TextSourceImpl;
import com.google.android.stardroid.units.GeocentricCoordinates;
import com.google.android.stardroid.units.RaDec;

import android.content.res.Resources;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a Layer which returns Sources which correspond to grid lines parallel
 * to the celestial equator and the hour angle. That is, returns a set of lines
 * with constant right ascension, and another set with constant declination.
 *
 * @author Brent Bryan
 * @author John Taylor
 */
public class GridLayer extends AbstractSourceLayer {
  private final int numRaSources;
  private final int numDecSources;

  public GridLayer(Resources resources, int numRightAscentionLines, int numDeclinationLines) {
    super(resources, false);
    this.numRaSources = numRightAscentionLines;
    this.numDecSources = numDeclinationLines;
  }

  @Override
  protected void initializeAstroSources(ArrayList<AstronomicalSource> sources) {
    sources.add(new GridSource(getResources(), numRaSources, numDecSources));
  }

  @Override
  public int getLayerId() {
    return -104;
  }

  @Override
  protected int getLayerNameId() {
    return R.string.show_grid_pref;  // TODO(johntaylor): rename this string Id.
  }
  
  // TODO(brent): Remove this.
  @Override
  public String getPreferenceId() {
    return "source_provider.4";
  }

  /** Implementation of the grid elements as an {@link AstronomicalSource} */
  static class GridSource extends AbstractAstronomicalSource {
    private static final int LINE_COLOR = Color.argb(20, 248, 239, 188);
    /** These are great (semi)circles, so only need 3 points. */
    private static final int NUM_DEC_VERTICES = 3;
    /** every 10 degrees */
    private static final int NUM_RA_VERTICES = 36;

    private final ArrayList<LineSourceImpl> lineSources = new ArrayList<LineSourceImpl>();
    private final ArrayList<TextSourceImpl> textSources = new ArrayList<TextSourceImpl>();

    public GridSource(Resources res, int numRaSources, int numDecSources) {
      for (int r = 0; r < numRaSources; r++) {
        lineSources.add(createRaLine(r, numRaSources));
      }
      for (int d = 0; d < numDecSources; d++) {
        lineSources.add(createDecLine(d, numDecSources));
      }

      /** North & South pole, hour markers every 2hrs. */
      textSources.add(new TextSourceImpl(0f, 90f, res.getString(R.string.north_pole), LINE_COLOR));
      textSources.add(new TextSourceImpl(0f, -90f, res.getString(R.string.south_pole), LINE_COLOR));
      for (int index = 0; index < 12; index++) {
        float ra = index * 30.0f;
        String title = String.format("%dh", 2 * index);
        textSources.add(new TextSourceImpl(ra, 0.0f, title, LINE_COLOR));
      }
    }

    /**
     * Constructs a single longitude line. These lines run from the north pole to
     * the south pole at fixed Right Ascensions.
     */
    private LineSourceImpl createRaLine(int index, int numRaSources) {
      LineSourceImpl line = new LineSourceImpl(LINE_COLOR);
      float ra = index * 360.0f / numRaSources;
      for (int i = 0; i < NUM_DEC_VERTICES - 1; i++) {
        float dec = 90.0f - i * 180.0f / (NUM_DEC_VERTICES - 1);
        RaDec raDec = new RaDec(ra, dec);
        line.raDecs.add(raDec);
        line.vertices.add(GeocentricCoordinates.getInstance(raDec));
      }
      RaDec raDec = new RaDec(0.0f, -90.0f);
      line.raDecs.add(raDec);
      line.vertices.add(GeocentricCoordinates.getInstance(raDec));
      return line;
    }

    private LineSourceImpl createDecLine(int index, int numDecSources) {
      LineSourceImpl line = new LineSourceImpl(LINE_COLOR);
      float dec = 90.0f - (index + 1.0f) * 180.0f / (numDecSources + 1.0f);
      for (int i = 0; i < NUM_RA_VERTICES; i++) {
        float ra = i * 360.0f / NUM_RA_VERTICES;
        RaDec raDec = new RaDec(ra, dec);
        line.raDecs.add(raDec);
        line.vertices.add(GeocentricCoordinates.getInstance(raDec));
      }
      RaDec raDec = new RaDec(0.0f, dec);
      line.raDecs.add(raDec);
      line.vertices.add(GeocentricCoordinates.getInstance(raDec));
      return line;
    }

    @Override
    public List<? extends TextSource> getLabels() {
      return textSources;
    }

    @Override
    public List<? extends LineSource> getLines() {
      return lineSources;
    }
  }
}
