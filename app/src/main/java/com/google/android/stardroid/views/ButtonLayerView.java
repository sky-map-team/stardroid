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

package com.google.android.stardroid.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.stardroid.R;

/**
 *  Contains the provider buttons.
 */

public class ButtonLayerView extends LinearLayout {

  public ButtonLayerView(Context context) {
    this(context, null);
  }

  public ButtonLayerView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setFocusable(false);
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ButtonLayerView);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {

    /* Consume all touch events so they don't get dispatched to the view
     * beneath this view.
     */
    return true;
  }

  public void show() {
    setVisibility(View.GONE);
  }

  public void hide() {
    setVisibility(View.GONE);
  }
  
  @Override
  public boolean hasFocus() {
    int numChildren = getChildCount();
    boolean hasFocus = false;
    for (int i = 0; i < numChildren; ++i) {
      hasFocus = hasFocus || getChildAt(i).hasFocus();
    }
    return hasFocus;
  }
}
