// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.renderer.debug;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.res.Resources;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.FloatMath;

import com.google.android.stardroid.renderer.SkyRenderer;
import com.google.android.stardroid.source.LinePrimitive;
import com.google.android.stardroid.source.PointPrimitive;
import com.google.android.stardroid.source.TextPrimitive;
import com.google.android.stardroid.math.CoordinateManipulations;

// This class is for debugging the sky renderer.
public class DebugSkyRenderer extends SkyRenderer {
  private boolean mEnableSearch = false;

  // If true, the look direction spins around in a circle
  private boolean mSpinLookDir = false;
  private float mLookAngle = 0;
  private float mLookAngleLowerBound = -(float) Math.PI / 4.0f;
  private float mLookAngleUpperBound = (float) Math.PI / 4.0f;

  private LabelMaker mLabels = null;
  private int mLabelFPS = 0;
  private Paint mLabelPaint = null;
  private NumericSprite mNumericSprite = null;
  private int mMsPerFrame;
  private int mFrames;
  private long mStartTime;
  private final static int SAMPLE_PERIOD_FRAMES = 12;
  private final static float SAMPLE_FACTOR = 1.0f / SAMPLE_PERIOD_FRAMES;

  public DebugSkyRenderer(Resources res) {
    super(res);

    mLabelPaint = new Paint();
    mLabelPaint.setTextSize(32);
    mLabelPaint.setAntiAlias(true);
    mLabelPaint.setARGB(0xff, 0xff, 0xff, 0xff);

    debugSetPointObjects();
//    debugSetPolyLineObjects();
//    debugSetLabelObjects();
//    debugSetImageObjects();
  }

  private void debugSetPointObjects() {
    GeocentricCoordinates coords = new GeocentricCoordinates(1f, 0f, 0f);
    PointPrimitive p1 = new PointPrimitive(coords, 0xff, 2);

//    PointPrimitive p2 = new PointPrimitive(0, 0, 0, 0, 0, 0, 2);
//    p2.xyz.x = 0;
//    p2.xyz.y = 1;
//    p2.xyz.z = 4;
//    p2.color = 0xff00; // green
//
//    PointPrimitive p3 = new PointPrimitive(0, 0, 0, 0, 0, 0, 2);
//    p3.xyz.x = 1;
//    p3.xyz.y = -1;
//    p3.xyz.z = 4;
//    p3.color = 0xff0000; // blue

    ArrayList<PointPrimitive> points = new ArrayList<PointPrimitive>();
    points.add(p1);
//    points.add(p2);
//    points.add(p3);
    // forceSetPointPrimitives(points, 0);
  }

  void debugSetPolyLineObjects() {
    LinePrimitive line1 = new LinePrimitive(0xffffffff);
    line1.vertices.add(new GeocentricCoordinates(-1, -1, 4));
    line1.vertices.add(new GeocentricCoordinates(0, 1, 4));
    line1.vertices.add(new GeocentricCoordinates(0, 0, 4));
    line1.vertices.add(new GeocentricCoordinates(1, 0, 4));

    LinePrimitive line2 = new LinePrimitive(0xffffffff);
    line2.vertices.add(new GeocentricCoordinates(1, -1.5f, 4));
    line2.vertices.add(new GeocentricCoordinates(0, -0.5f, 4));

    ArrayList<LinePrimitive> lines = new ArrayList<LinePrimitive>();
    lines.add(line1);
    lines.add(line2);

    // forceSetPolyLinePrimitives(lines, 0);
  }

  void debugSetLabelObjects() {
    ArrayList<TextPrimitive> labels = new ArrayList<TextPrimitive>();
    GeocentricCoordinates coords1 = new GeocentricCoordinates(0, 0, 4);
    TextPrimitive ts1 = new TextPrimitive(coords1, "Foo", 0xffffff00);

    GeocentricCoordinates coords2 = new GeocentricCoordinates(1, -1.5f, 4);
    TextPrimitive ts2 = new TextPrimitive(coords2, "Bar", 0xff00ffff);
    labels.add(ts1);
    labels.add(ts2);

    // forceSetTextPrimitives(labels, 0);
  }

  void debugSetImageObjects() {
    /*
     * TODO(serafini): Fix the unit tests.

    ArrayList<ImagePrimitive> images = new ArrayList<ImagePrimitive>();
    ImagePrimitive is1 = new ImagePrimitive(0.0f, 0, 0, null);
    is1.xyz.x = 0;
    is1.xyz.y = 0;
    is1.xyz.z = 5;

    is1.ux = 0.5f;
    is1.uy = 0.0f;
    is1.uz = 0.0f;

    is1.vx = 0.0f;
    is1.vy = 0.2f;
    is1.vz = 0.0f;

    ImagePrimitive is2 = new ImagePrimitive(0.0f, 0, 0, null);
    is2.xyz.x = 1;
    is2.xyz.y = 1;
    is2.xyz.z = 5;

    is2.ux = -0.4f;
    is2.uy = 0.0f;
    is2.uz = -0.4f;

    is2.vx = 0.0f;
    is2.vy = -0.3f;
    is2.vz = -0.3f;

    images.add(is1);
    images.add(is2);

    forceSetImagePrimitives(images, 0);
     */
  }


  @Override
  public void onDrawFrame(GL10 gl) {
    if (mSpinLookDir) {
      mLookAngle += 0.03f;
      if (mLookAngle > mLookAngleUpperBound) {
        mLookAngle = mLookAngleLowerBound;
      }
      super.setViewOrientation(1, 0.2f, 0, 0, FloatMath.sin(mLookAngle), FloatMath.cos(mLookAngle));
    }

    if (mEnableSearch) {
      super.enableSearchOverlay(new GeocentricCoordinates(0, 0.707f, 0.707f), "Foo");
      mEnableSearch = false;
    }

    super.onDrawFrame(gl);

    int width = getWidth();
    int height = getHeight();
    mLabels.beginDrawing(gl, width, height);
    float msPFX = width - mLabels.getWidth(mLabelFPS) - 1;
    mLabels.draw(gl, msPFX, 0, mLabelFPS);
    mLabels.endDrawing(gl);

    drawFPS(gl, msPFX);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    super.onSurfaceCreated(gl, config);

    mLabels = new LabelMaker(true, 256, 64);
    mLabels.initialize(gl, mTextureManager);
    mLabels.beginAdding(gl);
    mLabelFPS = mLabels.add(gl, "fps", mLabelPaint);
    mLabels.endAdding(gl);

    mNumericSprite = new NumericSprite();
    mNumericSprite.initialize(gl, mTextureManager, mLabelPaint);
  }

  private void drawFPS(GL10 gl, float rightMargin) {
    long time = SystemClock.uptimeMillis();
    if (mStartTime == 0) {
      mStartTime = time;
    }
    if (mFrames++ == SAMPLE_PERIOD_FRAMES) {
      mFrames = 0;
      long delta = time - mStartTime;
      mStartTime = time;
      mMsPerFrame = (int) (delta * SAMPLE_FACTOR);
    }
    if (mMsPerFrame > 0) {
      mNumericSprite.setValue(1000 / mMsPerFrame);
      float numWidth = mNumericSprite.width();
      float x = rightMargin - numWidth;
      mNumericSprite.draw(gl, x, 0, getWidth(), getHeight());
    }
  }
}
