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

package com.google.android.stardroid.layers;

import android.content.res.Resources;
import android.graphics.Color;
import android.util.Log;

import com.google.android.stardroid.R;
import com.google.android.stardroid.base.Lists;
import com.google.android.stardroid.base.TimeConstants;
import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.provider.ephemeris.OrbitalElements;
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType;
import com.google.android.stardroid.source.AbstractAstronomicalSource;
import com.google.android.stardroid.source.AstronomicalSource;
import com.google.android.stardroid.source.PointSource;
import com.google.android.stardroid.source.Sources;
import com.google.android.stardroid.source.TextSource;
import com.google.android.stardroid.source.impl.PointSourceImpl;
import com.google.android.stardroid.source.impl.TextSourceImpl;
import com.google.android.stardroid.units.GeocentricCoordinates;
import com.google.android.stardroid.util.MiscUtil;
import com.google.common.io.Closeables;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Brent Bryan
 */
public class IssLayer extends AbstractSourceLayer {
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private final AstronomerModel model;

  private IssSource issSource;

  public IssLayer(Resources resources, AstronomerModel model) {
    super(resources, true);
    this.model = model;
  }

  @Override
  protected void initializeAstroSources(ArrayList<AstronomicalSource> sources) {
    this.issSource = new IssSource(model, getResources());
    sources.add(issSource);

    scheduler.scheduleAtFixedRate(
        new OrbitalElementsGrabber(issSource), 0, 60, TimeUnit.SECONDS);
  }

  @Override
  public int getLayerDepthOrder() {
    return 70;
  }

  @Override
  protected int getLayerNameId() {
    return R.string.show_satellite_layer_pref;
  }

  /** Thread Runnable which parses the orbital elements out of the Url. */
  static class OrbitalElementsGrabber implements Runnable {
    private static final long UPDATE_FREQ_MS = TimeConstants.MILLISECONDS_PER_HOUR;
    private static final String TAG = MiscUtil.getTag(OrbitalElementsGrabber.class);
    private static final String URL_STRING = "http://spaceflight.nasa.gov/realdata/" +
        "sightings/SSapplications/Post/JavaSSOP/orbit/ISS/SVPOST.html";

    private final IssSource source;
    private long lastSuccessfulUpdateMs = -1L;

    public OrbitalElementsGrabber(IssSource source) {
      this.source = source;
    }

    /**
     * Parses the OrbitalElements from the given BufferedReader.  Factored out
     * of {@link #getOrbitalElements} to simplify testing.
     */
    OrbitalElements parseOrbitalElements(BufferedReader in) throws IOException {
      String s;
      while ((s = in.readLine()) != null && !s.contains("M50 Keplerian")) {}

      // Skip the dashed line
      in.readLine();

      float[] params = new float[9];
      int i = 0;
      for (; i < params.length && (s = in.readLine()) != null; i++) {
        s = s.substring(46).trim();
        String[] tokens = s.split("\\s+");
        params[i] = Float.parseFloat(tokens[2]);
      }


      if (i == params.length) {  // we read all the data.
        // TODO(serafini): Add magic here to create orbital elements or whatever.
        StringBuilder sb = new StringBuilder();
        for (int pi = 0; pi < params.length; pi++) {
          sb.append(" " + params[pi]);
        }
        //Blog.d(this, "Params: " + sb);
      }
      return null;
    }

    /**
     * Reads the given URL and returns the OrbitalElements associated with the object
     * described therein.
     */
    OrbitalElements getOrbitalElements(String urlString) {
      BufferedReader in = null;
      try {
        URLConnection connection = new URL(urlString).openConnection();
        in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        return parseOrbitalElements(in);
      } catch (IOException e) {
        Log.e(TAG, "Error reading Orbital Elements");
      } finally {
        Closeables.closeQuietly(in);
      }
      return null;
    }

    @Override
    public void run() {
      long currentTimeMs = System.currentTimeMillis();
      if ((currentTimeMs - lastSuccessfulUpdateMs) > UPDATE_FREQ_MS) {
        //Blog.d(this, "Fetching ISS data...");
        OrbitalElements elements = getOrbitalElements(URL_STRING);
        if (elements == null) {
          Log.d(TAG, "Error downloading ISS orbital data");
        } else {
          lastSuccessfulUpdateMs = currentTimeMs;
          source.setOrbitalElements(elements);
        }
      }
    }
  }

  /** AstronomicalSource corresponding to the International Space Station. */
  static class IssSource extends AbstractAstronomicalSource {
    private static final long UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_SECOND;
    private static final int ISS_COLOR = Color.YELLOW;

    private final GeocentricCoordinates coords = new GeocentricCoordinates(1f, 0f, 0f);
    private final ArrayList<PointSource> pointSources = new ArrayList<PointSource>();
    private final ArrayList<TextSource> textSources = new ArrayList<TextSource>();
    private final AstronomerModel model;
    private final String name;

    private OrbitalElements orbitalElements = null;
    private boolean orbitalElementsChanged;
    private long lastUpdateTimeMs = 0L;

    public IssSource(AstronomerModel model, Resources resources) {
      this.model = model;
      this.name = resources.getString(R.string.space_station);

      pointSources.add(new PointSourceImpl(coords, ISS_COLOR, 5));
      textSources.add(new TextSourceImpl(coords, name, ISS_COLOR));
    }

    public synchronized void setOrbitalElements(OrbitalElements elements) {
      this.orbitalElements = elements;
      orbitalElementsChanged = true;
    }

    @Override
    public List<String> getNames() {
      return Lists.asList(name);
    }

    @Override
    public GeocentricCoordinates getSearchLocation() {
      return coords;
    }

    private void updateCoords(Date time) {
      lastUpdateTimeMs = time.getTime();
      orbitalElementsChanged = false;

      if (orbitalElements == null) {
        return;
      }
      // TODO(serafini): Update coords of Iss from OrbitalElements.
      // issCoords.assign(...);
    }

    @Override
    public Sources initialize() {
      updateCoords(model.getTime());
      return this;
    }

    @Override
    public synchronized EnumSet<UpdateType> update() {
      EnumSet<UpdateType> updateTypes = EnumSet.noneOf(UpdateType.class);

      Date modelTime = model.getTime();
      if (orbitalElementsChanged ||
          Math.abs(modelTime.getTime() - lastUpdateTimeMs) > UPDATE_FREQ_MS) {

        updateCoords(modelTime);
        if (orbitalElements != null) {
          updateTypes.add(UpdateType.UpdatePositions);
        }
      }
      return updateTypes;
    }

    @Override
    public List<? extends TextSource> getLabels() {
      return textSources;
    }

    @Override
    public List<? extends PointSource> getPoints() {
      return pointSources;
    }
  }


}
