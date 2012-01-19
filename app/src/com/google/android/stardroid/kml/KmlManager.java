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

package com.google.android.stardroid.kml;

import com.google.android.stardroid.layers.LayerManager;
import com.google.android.stardroid.util.MiscUtil;

import android.util.Log;

/**
 * Manages KML files.
 * Further on, we probably want to manage lists of KML files previously seen,
 * cache them locally, have callbacks for when loading is complete etc....
 * ...but this will do for now.
 * @author John Taylor
 */
public class KmlManager {
  private static final String TAG = MiscUtil.getTag(KmlManager.class);
  private LayerManager layerManager;

  public KmlManager(LayerManager layerManager) {
    this.layerManager = layerManager;
  }

  public void loadKmlLayer(String kmlUrl) throws KmlException {
    // TODO(schulman): feel free to plug in here or replace this class.
    Log.i(TAG, "Loading kml from " + kmlUrl);
  }

}
