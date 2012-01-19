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

package com.google.android.stardroid.views;

import android.os.Handler;

/**
 * Controls the appearance and disappearance of the widgets.
 * Main purpose is to show the widgets when the screen is tapped, and
 * then fade them away after a certain period of inactivity.
 */
public class WidgetFader implements Runnable {
  public interface Fadeable {
    void show();
    void hide();
  }
  private Fadeable controls;
  private boolean visible;
  private Handler handler;
  private int timeOut = 1500;
  
  public WidgetFader(Fadeable controls) {
    this.controls = controls;
    this.handler = new Handler();
  }
  
  public WidgetFader(Fadeable controls, int timeOut) {
    this.controls = controls;
    this.handler = new Handler();
    this.timeOut = timeOut;
  }
  
  private void makeVisible() {
    if (visible) return;
    visible = true;
    controls.show();
  }
  
  public void keepActive() {
    makeVisible();
    handler.removeCallbacks(this);
    handler.postDelayed(this, timeOut);
  }
  
  private void makeInactive() {
    if (!visible) return;
    visible = false;
    controls.hide();
  }

  public void run() {
    makeInactive();
  }
}
