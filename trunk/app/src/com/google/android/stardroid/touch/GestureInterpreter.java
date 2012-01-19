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

import com.google.android.stardroid.touch.Flinger.FlingListener;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.views.WidgetFader;

import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;

/**
 * Processes touch events and scrolls the screen in manual mode.
 *
 * @author John Taylor
 */
public class GestureInterpreter extends GestureDetector.SimpleOnGestureListener {
  private static final String TAG = MiscUtil.getTag(GestureInterpreter.class);
  private WidgetFader[] faders;
  private MapMover mapMover;

  public GestureInterpreter(WidgetFader[] faders,
                            MapMover mapMover) {
    this.faders = faders;
    this.mapMover = mapMover;
  }

  private final Flinger flinger = new Flinger(new FlingListener() {
    public void fling(float distanceX, float distanceY) {
      mapMover.onDrag(distanceX, distanceY);
    }
  });

  @Override
  public boolean onDown(MotionEvent e) {
    Log.d(TAG, "Tap down");
    flinger.stop();
    return true;
  }

  @Override
  public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    Log.d(TAG, "Flinging " + velocityX + ", " + velocityY);
    flinger.fling(velocityX, velocityY);
    return true;
  }

  @Override
  public boolean onSingleTapUp(MotionEvent e) {
    Log.d(TAG, "Tap up");
    // Bring up the controls
    for (WidgetFader fader : faders) {
      fader.keepActive();
    }
    return true;
  }

  @Override
  public boolean onDoubleTap(MotionEvent e) {
    Log.d(TAG, "Double tap");
    return false;
  }

  @Override
  public boolean onSingleTapConfirmed(MotionEvent e) {
    Log.d(TAG, "Confirmed single tap");
    return false;
  }
  
}
