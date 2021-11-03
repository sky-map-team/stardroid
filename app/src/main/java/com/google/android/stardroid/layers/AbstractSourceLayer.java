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

import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType;
import com.google.android.stardroid.renderer.util.AbstractUpdateClosure;
import com.google.android.stardroid.renderer.util.UpdateClosure;
import com.google.android.stardroid.search.PrefixStore;
import com.google.android.stardroid.search.SearchResult;
import com.google.android.stardroid.source.AstronomicalSource;
import com.google.android.stardroid.source.ImagePrimitive;
import com.google.android.stardroid.source.LinePrimitive;
import com.google.android.stardroid.source.PointPrimitive;
import com.google.android.stardroid.source.Sources;
import com.google.android.stardroid.source.TextPrimitive;
import com.google.android.stardroid.math.Vector3;
import com.google.android.stardroid.util.MiscUtil;

import android.content.res.Resources;
import android.util.Log;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Layer for objects which are {@link AstronomicalSource}s.
 *
 * @author Brent Bryan
 */
// TODO(brent): merge with AbstractLayer?
public abstract class AbstractSourceLayer extends AbstractLayer {
  private static final String TAG = MiscUtil.getTag(AbstractSourceLayer.class);

  private final ArrayList<TextPrimitive> textPrimitives = new ArrayList<TextPrimitive>();
  private final ArrayList<ImagePrimitive> imageSources = new ArrayList<ImagePrimitive>();
  private final ArrayList<PointPrimitive> pointPrimitives = new ArrayList<PointPrimitive>();
  private final ArrayList<LinePrimitive> linePrimitives = new ArrayList<LinePrimitive>();
  private final ArrayList<AstronomicalSource> astroSources = new ArrayList<AstronomicalSource>();

  private HashMap<String, SearchResult> searchIndex = new HashMap<String, SearchResult>();
  private PrefixStore prefixStore = new PrefixStore();
  private final boolean shouldUpdate;
  private SourceUpdateClosure closure;

  public AbstractSourceLayer(Resources resources, boolean shouldUpdate) {
    super(resources);
    this.shouldUpdate = shouldUpdate;
  }

  @Override
  public synchronized void initialize() {
    astroSources.clear();

    initializeAstroSources(astroSources);

    for (AstronomicalSource astroSource : astroSources) {
      Sources sources = astroSource.initialize();

      textPrimitives.addAll(sources.getLabels());
      imageSources.addAll(sources.getImages());
      pointPrimitives.addAll(sources.getPoints());
      linePrimitives.addAll(sources.getLines());

      List<String> names = astroSource.getNames();
      if (!names.isEmpty()) {
        Vector3 searchLoc = astroSource.getSearchLocation();
        for (String name : names) {
          searchIndex.put(name.toLowerCase(), new SearchResult(name, searchLoc));
          prefixStore.add(name.toLowerCase());
        }
      }
    }

    // update the renderer
    updateLayerForControllerChange();
  }

  @Override
  protected void updateLayerForControllerChange() {
    refreshSources(EnumSet.of(UpdateType.Reset));
    if (shouldUpdate) {
      if (closure == null) {
        closure = new SourceUpdateClosure(this);
      }
      addUpdateClosure(closure);
    }
  }

  /**
   * Subclasses should override this method and add all their
   * {@link AstronomicalSource} to the given {@link ArrayList}.
   */
  protected abstract void initializeAstroSources(ArrayList<AstronomicalSource> sources);

  /**
   * Redraws the sources on this layer, after first refreshing them based on
   * the current state of the
   * {@link com.google.android.stardroid.control.AstronomerModel}.
   */
  protected void refreshSources() {
    refreshSources(EnumSet.noneOf(UpdateType.class));
  }

  /**
   * Redraws the sources on this layer, after first refreshing them based on
   * the current state of the
   * {@link com.google.android.stardroid.control.AstronomerModel}.
   */
  protected synchronized void refreshSources(EnumSet<UpdateType> updateTypes) {
    for (AstronomicalSource astroSource : astroSources) {
      updateTypes.addAll(astroSource.update());
    }

    if (!updateTypes.isEmpty()) {
      redraw(updateTypes);
    }
  }

  /**
   * Forcefully resets and redraws all sources on this layer everything on
   * this layer.
   */
  @Override
  protected void redraw() {
    refreshSources(EnumSet.of(UpdateType.Reset));
  }

  private final void redraw(EnumSet<UpdateType> updateTypes) {
    super.redraw(textPrimitives, pointPrimitives, linePrimitives, imageSources, updateTypes);
  }

  @Override
  public List<SearchResult> searchByObjectName(String name) {
    Log.d(TAG, "Search planets layer for " + name);
    List<SearchResult> matches = new ArrayList<SearchResult>();
    SearchResult searchResult = searchIndex.get(name.toLowerCase());
    if (searchResult != null) {
      matches.add(searchResult);
    }
    Log.d(TAG, getLayerName() + " provided " + matches.size() + "results for " + name);
    return matches;
  }

  @Override
  public Set<String> getObjectNamesMatchingPrefix(String prefix) {
    Log.d(TAG, "Searching planets layer for prefix " + prefix);
    Set<String> results = prefixStore.queryByPrefix(prefix);
    Log.d(TAG, "Got " + results.size() + " results for prefix " + prefix + " in " + getLayerName());
    return results;
  }

  /** Implementation of the {@link UpdateClosure} interface used to update a layer */
  public static class SourceUpdateClosure extends AbstractUpdateClosure {
    private final AbstractSourceLayer layer;

    public SourceUpdateClosure(AbstractSourceLayer layer) {
      this.layer = layer;
    }

    @Override
    public void run() {
      layer.refreshSources();
    }
  }
}
