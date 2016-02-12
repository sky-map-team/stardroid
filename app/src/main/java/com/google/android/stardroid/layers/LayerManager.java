// Copyright 2009 Google Inc.
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

import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.renderer.RendererController;
import com.google.android.stardroid.search.SearchResult;
import com.google.android.stardroid.search.SearchTermsProvider.SearchTerm;
import com.google.android.stardroid.util.MiscUtil;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Allows a group of layers to be controlled together.
 */
public class LayerManager implements OnSharedPreferenceChangeListener {
  private static final String TAG = MiscUtil.getTag(LayerManager.class);
  private final ArrayList<Layer> layers = new ArrayList<Layer>();
  private final SharedPreferences sharedPreferences;

  // TODO(johntaylor): delete the model parameter
  public LayerManager(SharedPreferences sharedPreferences, AstronomerModel model) {
    this.sharedPreferences = sharedPreferences;
    sharedPreferences.registerOnSharedPreferenceChangeListener(this);
  }

  public void addLayer(Layer layer) {
    this.layers.add(layer);
  }

  public void initialize() {
    for (Layer layer : layers) {
      layer.initialize();
    }
  }

  public void registerWithRenderer(RendererController renderer) {
    for (Layer layer : layers) {
      layer.registerWithRenderer(renderer);
      String prefId = layer.getPreferenceId();
      boolean visible = sharedPreferences.getBoolean(prefId, true);
      layer.setVisible(visible);
    }
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    for (Layer layer : layers) {
      if (layer.getPreferenceId().equals(key)) {
        boolean visible = prefs.getBoolean(key, true);
        layer.setVisible(visible);
      }
    }
  }

  /**
   * Returns the name of this object.
   */
  public String getName() {
    return "Layer Manager";
  }

  /**
   * Search all visible layers for an object with the given name.
   * @param name the name to search for
   * @return a list of all matching objects.
   */
  public List<SearchResult> searchByObjectName(String name) {
    ArrayList<SearchResult> all = new ArrayList<SearchResult>();
    for (Layer layer : layers) {
      if (isLayerVisible(layer)) {
        all.addAll(layer.searchByObjectName(name));
      }
    }
    Log.d(TAG, "Got " + all.size() + " results in total for " + name);
    return all;
  }

  /**
   * Given a string prefix, find all possible queries for which we have a
   * result in the visible layers.
   * @param prefix the prefix to search for.
   * @return a set of matching queries.
   */
  public Set<SearchTerm> getObjectNamesMatchingPrefix(String prefix) {
    HashSet<SearchTerm> all = new HashSet<SearchTerm>();
    for (Layer layer : layers) {
      if (isLayerVisible(layer)) {
        for (String query : layer.getObjectNamesMatchingPrefix(prefix)) {
          SearchTerm result = new SearchTerm(query, layer.getLayerName());
          all.add(result);
        }
      }
    }
    Log.d(TAG, "Got " + all.size() + " results in total for " + prefix);
    return all;
  }

  private boolean isLayerVisible(Layer layer) {
    return sharedPreferences.getBoolean(layer.getPreferenceId(), true);
  }
}
