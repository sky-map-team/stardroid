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

import android.content.res.Resources;
import android.graphics.Color;

import com.google.android.stardroid.R;
import com.google.android.stardroid.base.Lists;
import com.google.android.stardroid.base.TimeConstants;
import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType;
import com.google.android.stardroid.source.AbstractAstronomicalSource;
import com.google.android.stardroid.source.AstronomicalSource;
import com.google.android.stardroid.source.Sources;
import com.google.android.stardroid.source.LinePrimitive;
import com.google.android.stardroid.source.TextPrimitive;
import com.google.android.stardroid.math.Vector3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Creates a mark at the zenith, nadir and cardinal point and a horizon.
 *
 * @author Brent Bryan
 * @author John Taylor
 */
public class HorizonLayer extends AbstractSourceLayer {
  private final AstronomerModel model;

  public HorizonLayer(AstronomerModel model, Resources resources) {
    super(resources, true);
    this.model = model;
  }

  @Override
  protected void initializeAstroSources(ArrayList<AstronomicalSource> sources) {
    sources.add(new HorizonSource(model, getResources()));
  }

  @Override
  public int getLayerDepthOrder() {
    return 90;
  }
  
  // TODO(brent): Remove this.
  @Override
  public String getPreferenceId() {
    return "source_provider.5";
  }

  @Override
  public String getLayerName() {
    // TODO(johntaylor): i18n
    return "Horizon";
  }

  @Override
  protected int getLayerNameId() {
    return R.string.show_horizon_pref;  // TODO(johntaylor): rename this string id
  }

  /** Implementation of {@link AstronomicalSource} for the horizon source. */
  static class HorizonSource extends AbstractAstronomicalSource {
    // Due to a bug in the G1 rendering code text and lines render in different
    // colors.
    private static final int LINE_COLOR = Color.argb(120, 86, 176, 245);
    private static final int LABEL_COLOR = Color.argb(120, 245, 176, 86);
    private static final long UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_SECOND;

    private final Vector3 zenith = new Vector3(0, 0, 0);
    private final Vector3 nadir = new Vector3(0, 0, 0);
    private final Vector3 north = new Vector3(0, 0, 0);
    private final Vector3 south = new Vector3(0, 0, 0);
    private final Vector3 east = new Vector3(0, 0, 0);
    private final Vector3 west = new Vector3(0, 0, 0);

    private final ArrayList<LinePrimitive> linePrimitives = new ArrayList<LinePrimitive>();
    private final ArrayList<TextPrimitive> textPrimitives = new ArrayList<TextPrimitive>();
    private final AstronomerModel model;

    private long lastUpdateTimeMs = 0L;

    public HorizonSource(AstronomerModel model, Resources res) {
      this.model = model;

      List<Vector3> vertices = Lists.asList(north, east, south, west, north);
      linePrimitives.add(new LinePrimitive(LINE_COLOR, vertices, 1.5f));

      textPrimitives.add(new TextPrimitive(zenith, res.getString(R.string.zenith), LABEL_COLOR));
      textPrimitives.add(new TextPrimitive(nadir, res.getString(R.string.nadir), LABEL_COLOR));
      textPrimitives.add(new TextPrimitive(north, res.getString(R.string.north), LABEL_COLOR));
      textPrimitives.add(new TextPrimitive(south, res.getString(R.string.south), LABEL_COLOR));
      textPrimitives.add(new TextPrimitive(east, res.getString(R.string.east), LABEL_COLOR));
      textPrimitives.add(new TextPrimitive(west, res.getString(R.string.west), LABEL_COLOR));
    }

    private void updateCoords() {
      // Blog.d(this, "Updating Coords: " + (model.getTime().getTime() - lastUpdateTimeMs));

      this.lastUpdateTimeMs = model.getTime().getTime();
      this.zenith.assign(model.getZenith());
      this.nadir.assign(model.getNadir());
      this.north.assign(model.getNorth());
      this.south.assign(model.getSouth());
      this.east.assign(model.getEast());
      this.west.assign(model.getWest());
    }

    @Override
    public Sources initialize() {
      updateCoords();
      return this;
    }

    @Override
    public EnumSet<UpdateType> update() {
      EnumSet<UpdateType> updateTypes = EnumSet.noneOf(UpdateType.class);

      // TODO(brent): Add distance here.
      if (Math.abs(model.getTime().getTime() - lastUpdateTimeMs) > UPDATE_FREQ_MS) {
        updateCoords();
        updateTypes.add(UpdateType.UpdatePositions);
      }
      return updateTypes;
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
