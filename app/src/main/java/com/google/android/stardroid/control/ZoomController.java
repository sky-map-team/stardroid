// Copyright 2008 Google Inc.
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

package com.google.android.stardroid.control;

import android.util.Log;

import com.google.android.stardroid.util.MiscUtil;

/**
 * Controls the field of view of a user.
 *
 * @author John Taylor
 */
public class ZoomController extends AbstractController {
  private static final String TAG = MiscUtil.getTag(ZoomController.class);
  public static final float ZOOM_FACTOR = (float) Math.pow(1.5, 0.0625);
  public static final float MAX_ZOOM_OUT = 90.0f;

  /**
   * Decreases the field of view by {@link #ZOOM_FACTOR}.
   */
  public void zoomIn() {
    zoomBy(1.0f / ZOOM_FACTOR);
  }

  /**
   * Increases the field of view by {@link #ZOOM_FACTOR}.
   */
  public void zoomOut() {
    zoomBy(ZOOM_FACTOR);
  }

  private void setFieldOfView(float zoomDegrees) {
    if (!enabled) {
      return;
    }
    Log.d(TAG, "Setting field of view to " + zoomDegrees);
    model.setFieldOfView(zoomDegrees);
  }

  @Override
  public void start() {
    // Nothing to do
  }

  @Override
  public void stop() {
    // Nothing to do
  }

  public void zoomBy(float ratio) {
    float zoomDegrees = model.getFieldOfView();
    zoomDegrees = Math.min(zoomDegrees * ratio, MAX_ZOOM_OUT);
    setFieldOfView(zoomDegrees);
  }
}
