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

package com.google.android.stardroid.touch;

import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.control.ControllerGroup;
import com.google.android.stardroid.util.Geometry;
import com.google.android.stardroid.util.MiscUtil;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

/**
 * Applies drags, zooms and rotations to the model.
 * Listens for events from the DragRotateZoomGestureDetector.
 *
 * @author John Taylor
 */
public class MapMover implements
    DragRotateZoomGestureDetector.DragRotateZoomGestureDetectorListener,
    OnSharedPreferenceChangeListener {

  private static final String TAG = MiscUtil.getTag(MapMover.class);
  private static final String ALLOW_ROTATION = "allow_rotation";
  private AstronomerModel model;
  private ControllerGroup controllerGroup;
  private float sizeTimesRadiansToDegrees;
  // Some phones, such as the Nexus 1, only support Duo-touch which means that
  // rotation is unreliable.  So we allow it to be disabled for users who find
  // the effect disconcerting.
  private boolean allowRotation;

  public MapMover(AstronomerModel model, ControllerGroup controllerGroup, Context context,
                  SharedPreferences sharedPreferences) {
    this.model = model;
    this.controllerGroup = controllerGroup;
    Display display = ((WindowManager) context.getSystemService(
        Context.WINDOW_SERVICE)).getDefaultDisplay(); 
    int screenLongSize = display.getHeight();
    Log.i(TAG, "Screen height is " + screenLongSize + " pixels.");
    sizeTimesRadiansToDegrees = screenLongSize * Geometry.RADIANS_TO_DEGREES;
    sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    allowRotation = sharedPreferences.getBoolean(ALLOW_ROTATION, true);
  }

  @Override
  public boolean onDrag(float xPixels, float yPixels) {
    // Log.d(TAG, "Dragging by " + xPixels + ", " + yPixels);
    final float pixelsToRadians = model.getFieldOfView() / sizeTimesRadiansToDegrees;
    controllerGroup.changeUpDown(-yPixels * pixelsToRadians);
    controllerGroup.changeRightLeft(-xPixels * pixelsToRadians);
    return true;
  }

  @Override
  public boolean onRotate(float degrees) {
    if (allowRotation) {
      controllerGroup.rotate(-degrees);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean onStretch(float ratio) {
    controllerGroup.zoomBy(1.0f / ratio);
    return true;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
      String key) {
    allowRotation = sharedPreferences.getBoolean(ALLOW_ROTATION, true);
  }
}
