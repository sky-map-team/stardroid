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

package com.google.android.stardroid.renderer;

import static com.google.android.stardroid.math.MathUtilsKt.DEGREES_TO_RADIANS;

import com.google.android.stardroid.R;
import com.google.android.stardroid.math.MathUtils;
import com.google.android.stardroid.math.Vector3;
import com.google.android.stardroid.renderer.util.IndexBuffer;
import com.google.android.stardroid.renderer.util.NightVisionColorBuffer;
import com.google.android.stardroid.renderer.util.TexCoordBuffer;
import com.google.android.stardroid.renderer.util.TextureManager;
import com.google.android.stardroid.renderer.util.TextureReference;
import com.google.android.stardroid.renderer.util.VertexBuffer;
import com.google.android.stardroid.renderables.LinePrimitive;

import java.util.EnumSet;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

public class PolyLineObjectManager extends RendererObjectManager {
  private VertexBuffer mVertexBuffer = new VertexBuffer(true);
  private NightVisionColorBuffer mColorBuffer = new NightVisionColorBuffer(true);
  private TexCoordBuffer mTexCoordBuffer = new TexCoordBuffer(true);
  private IndexBuffer mIndexBuffer = new IndexBuffer(true);
  private TextureReference mTexRef = null;
  private boolean mOpaque = true;
  
  public PolyLineObjectManager(int layer, TextureManager textureManager) {
    super(layer, textureManager);
  }
  
  public void updateObjects(List<LinePrimitive> lines, EnumSet<UpdateType> updateType) {
    // We only care about updates to positions, ignore any other updates.
    if (!updateType.contains(UpdateType.Reset) && 
        !updateType.contains(UpdateType.UpdatePositions)) {
      return;
    }
    int numLineSegments = 0;
    for (LinePrimitive l : lines) {
      numLineSegments += l.getVertices().size() - 1;
    }
    
    // To render everything in one call, we render everything as a line list
    // rather than a series of line strips.
    int numVertices = 4 * numLineSegments;
    int numIndices = 6 * numLineSegments;
    
    VertexBuffer vb = mVertexBuffer;
    vb.reset(4 * numLineSegments);
    NightVisionColorBuffer cb = mColorBuffer;
    cb.reset(4 * numLineSegments);
    TexCoordBuffer tb = mTexCoordBuffer;
    tb.reset(numVertices);
    IndexBuffer ib = mIndexBuffer;
    ib.reset(numIndices);
    
    // See comment in PointObjectManager for justification of this calculation.
    float fovyInRadians = 60 * DEGREES_TO_RADIANS;
    float sizeFactor = MathUtils.tan(fovyInRadians * 0.5f) / 480;
    
    boolean opaque = true;
    
    short vertexIndex = 0;
    for (LinePrimitive l : lines) {
      List<Vector3> coords = l.getVertices();
      if (coords.size() < 2)
        continue;

      // If the color isn't fully opaque, set opaque to false.
      int color = l.getColor();
      opaque &= (color & 0xff000000) == 0xff000000;
      
      // Add the vertices.
      for (int i = 0; i < coords.size() - 1; i++) {
        Vector3 p1 = coords.get(i);
        Vector3 p2 = coords.get(i+1);
        Vector3 u = p2.minus(p1);
        // The normal to the quad should face the origin at its midpoint.
        Vector3 avg = p1.plus(p2);
        avg.timesAssign(0.5f);
        // I'm assuming that the points will already be on a unit sphere.  If this is not the case,
        // then we should normalize it here.
        Vector3 v = u.times(avg).normalizedCopy();
        v.timesAssign(sizeFactor * l.getLineWidth());
        
        
        // Add the vertices
        
        // Lower left corner
        vb.addPoint(p1.minus(v));
        cb.addColor(color);
        tb.addTexCoords(0, 1);
        
        // Upper left corner
        vb.addPoint(p1.plus(v));
        cb.addColor(color);
        tb.addTexCoords(0, 0);
        
        // Lower left corner
        vb.addPoint(p2.minus(v));
        cb.addColor(color);
        tb.addTexCoords(1, 1);
        
        // Upper left corner
        vb.addPoint(p2.plus(v));
        cb.addColor(color);
        tb.addTexCoords(1, 0);
        
        
        // Add the indices
        short bottomLeft = vertexIndex++;
        short topLeft = vertexIndex++;
        short bottomRight = vertexIndex++;
        short topRight = vertexIndex++;
        
        // First triangle
        ib.addIndex(bottomLeft);
        ib.addIndex(topLeft);
        ib.addIndex(bottomRight);

        // Second triangle
        ib.addIndex(bottomRight);
        ib.addIndex(topLeft);
        ib.addIndex(topRight);
      }
    }
    mOpaque = opaque;
  }
  
  @Override
  public void reload(GL10 gl, boolean fullReload) {
    mTexRef = textureManager().getTextureFromResource(gl, R.drawable.line);
    mVertexBuffer.reload();
    mColorBuffer.reload();
    mTexCoordBuffer.reload();
    mIndexBuffer.reload();
  }
  
  @Override
  protected void drawInternal(GL10 gl) {
    if (mIndexBuffer.size() == 0)
      return;
    
    gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
    gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
    gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
    
    gl.glEnable(GL10.GL_TEXTURE_2D);
    mTexRef.bind(gl);
    
    gl.glEnable(GL10.GL_CULL_FACE);
    gl.glFrontFace(GL10.GL_CW);
    gl.glCullFace(GL10.GL_BACK);
    
    if (!mOpaque) {
      gl.glEnable(GL10.GL_BLEND);
      gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
    }

    gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_MODULATE);
        
    mVertexBuffer.set(gl);
    mColorBuffer.set(gl, getRenderState().getNightVisionMode());
    mTexCoordBuffer.set(gl);

    mIndexBuffer.draw(gl, GL10.GL_TRIANGLES);
    
    if (!mOpaque) {
      gl.glDisable(GL10.GL_BLEND);
    }
    
    gl.glDisable(GL10.GL_TEXTURE_2D);
    gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
  }
}
