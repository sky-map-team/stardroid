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

package com.google.android.stardroid.renderer;

import android.content.res.Resources;
import android.graphics.Paint;

import com.google.android.stardroid.renderer.util.IndexBuffer;
import com.google.android.stardroid.renderer.util.LabelMaker;
import com.google.android.stardroid.renderer.util.TextureManager;
import com.google.android.stardroid.renderer.util.TextureReference;
import com.google.android.stardroid.renderer.util.VertexBuffer;

import javax.microedition.khronos.opengles.GL10;

/**
 * Manages rendering of which appears at fixed points on the screen, rather
 * than text which appears at fixed points in the world.
 * 
 * @author James Powell
 *
 */
public class LabelOverlayManager {
  private Label[] mLabels = null;
  private LabelMaker mLabelMaker = new LabelMaker(true);
  private Paint mLabelPaint = new Paint();
  private TextureReference mTexture = null;
  private VertexBuffer mVertexBuffer = null;
  private IndexBuffer mIndexBuffer = null;
  
  public static class Label extends LabelMaker.LabelData {
    public Label(String text, int color, int size) {
      super(text, color, size);
    }
    
    public boolean enabled() {
      return mEnabled;
    }
    public void setEnabled(boolean enabled) {
      mEnabled = enabled;
    }
    
    public void setPosition(int x, int y) {
      mX = x;
      mY = y;
    }
    
    public void setAlpha(float alpha) {
      mAlpha = alpha;
    }
    
    private float getAlpha() {
      return mAlpha;
    }
    
    private boolean mEnabled = true;
    private int mX = 0, mY = 0;
    private float mAlpha = 1;
  }
  
  public LabelOverlayManager() {
    mLabelPaint.setAntiAlias(true);
    
    mVertexBuffer = new VertexBuffer(4, false);
    mIndexBuffer = new IndexBuffer(6);
    
    mVertexBuffer.addPoint(0, 0, 0);  // Bottom left
    mVertexBuffer.addPoint(0, 1, 0);  // Top left
    mVertexBuffer.addPoint(1, 0, 0);  // Bottom right
    mVertexBuffer.addPoint(1, 1, 0);  // Top right
    
    // Triangle one: bottom left, top left, bottom right.  
    mIndexBuffer.addIndex((short) 0);
    mIndexBuffer.addIndex((short) 1);
    mIndexBuffer.addIndex((short) 2);
    
    // Triangle two: bottom right, top left, top right.
    mIndexBuffer.addIndex((short) 2);
    mIndexBuffer.addIndex((short) 1);
    mIndexBuffer.addIndex((short) 3);
  }
  
  public void initialize(GL10 gl, Label[] labels, Resources res,
                         TextureManager textureManager) {
    mLabels = labels.clone();
    mTexture = mLabelMaker.initialize(gl, mLabelPaint, labels, res, textureManager);
  }
  
  public void releaseTexture(GL10 gl) {
    // TODO(jpowell): Figure out if LabelMaker should have a shutdown() method
    // and delete the texture or if I should do it myself.
    if (mTexture != null) {
      mLabelMaker.shutdown(gl);
      mTexture = null;
    }
  }
  
  public void draw(GL10 gl, int screenWidth, int screenHeight) {
    if (mLabels == null || mTexture == null) {
      return;
    }
    
    gl.glEnable(GL10.GL_TEXTURE_2D);
    mTexture.bind(gl);
    
    gl.glEnable(GL10.GL_BLEND);
    gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
    
    gl.glTexEnvx(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, 
                 GL10.GL_MODULATE);
    
    gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
    gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
    gl.glDisableClientState(GL10.GL_COLOR_ARRAY);

    // Change to orthographic projection, where the units in model view space
    // are the same as in screen space.
    gl.glMatrixMode(GL10.GL_PROJECTION);
    gl.glPushMatrix();
    gl.glLoadIdentity();
    gl.glOrthof(0, screenWidth, 0, screenHeight, -100, 100);
    
    gl.glMatrixMode(GL10.GL_MODELVIEW);
    gl.glPushMatrix();

    for (Label label : mLabels) {
      if (label.enabled()) {        
        int x = label.mX - label.getWidthInPixels() / 2;
        int y = label.mY;
        
        gl.glLoadIdentity();

        // Move the label to the correct offset.
        gl.glTranslatef(x, y, 0.0f);
        
        // Scale the label to the correct size.
        gl.glScalef(label.getWidthInPixels(), label.getHeightInPixels(), 0.0f);

        // Set the alpha for the label.
        gl.glColor4f(1, 1, 1, label.getAlpha());
        
        // Draw the label.
        mVertexBuffer.set(gl);
        gl.glTexCoordPointer(2, GL10.GL_FIXED, 0, label.getTexCoords());
        mIndexBuffer.draw(gl, GL10.GL_TRIANGLES);
      }
    }

    // Restore the old matrices.
    gl.glMatrixMode(GL10.GL_PROJECTION);
    gl.glPopMatrix();
    
    gl.glMatrixMode(GL10.GL_MODELVIEW);
    gl.glPopMatrix();

    gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);
    gl.glDisable(GL10.GL_BLEND);
    gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
    
    gl.glDisable(GL10.GL_TEXTURE_2D);
  }
}
