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

package com.google.android.stardroid.util.smoothers;

import android.hardware.SensorListener;
import android.util.Log;

import com.google.android.stardroid.util.MiscUtil;


public abstract class SmootherModelAdaptor implements SensorListener {

  private static final String TAG = MiscUtil.getTag(SmootherModelAdaptor.class);

  public void shutDown() {
    Log.d(TAG, this + " shutting down.");
    setLive(false);
  }

  public void start() {
    setLive(true);
  }

  protected synchronized void setLive(boolean b) {
    live = b;
  }

  protected synchronized boolean isLive() {
    return live;
  }

  private boolean live;

  @Override
  public void onAccuracyChanged(int sensor, int accuracy) {
    // Do nothing
  }
}
