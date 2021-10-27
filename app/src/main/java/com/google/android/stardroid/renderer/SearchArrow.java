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

import static com.google.android.stardroid.math.MathUtilsKt.PI;
import static com.google.android.stardroid.math.MathUtilsKt.TWO_PI;

import android.content.res.Resources;

import com.google.android.stardroid.R;
import com.google.android.stardroid.math.MathUtils;
import com.google.android.stardroid.math.Vector3;
import com.google.android.stardroid.renderer.util.SearchHelper;
import com.google.android.stardroid.renderer.util.TextureManager;
import com.google.android.stardroid.renderer.util.TextureReference;
import com.google.android.stardroid.renderer.util.TexturedQuad;
import com.google.android.stardroid.util.FixedPoint;

import javax.microedition.khronos.opengles.GL10;

public class SearchArrow {
  // The arrow quad is 10% of the screen width or height, whichever is smaller.
  private final float ARROW_SIZE = 0.1f;
  // The circle quad is 40% of the screen width or height, whichever is smaller.
  private final float CIRCLE_SIZE = 0.4f;
  
  // The target position is (1, theta, phi) in spherical coordinates.
  private float mTargetTheta = 0;
  private float mTargetPhi = 0;
  private TexturedQuad mCircleQuad = null;
  private TexturedQuad mArrowQuad = null;
  private float mArrowOffset = 0;
  private float mCircleSizeFactor = 1;
  private float mArrowSizeFactor = 1;
  private float mFullCircleScaleFactor = 1;
  
  private TextureReference mArrowTex = null;
  private TextureReference mCircleTex = null;
  
  public void reloadTextures(GL10 gl, Resources res, TextureManager textureManager) {
    gl.glEnable(GL10.GL_TEXTURE_2D);
    
    mArrowTex = textureManager.getTextureFromResource(gl, R.drawable.arrow);    
    mCircleTex = textureManager.getTextureFromResource(gl, R.drawable.arrowcircle);
    
    gl.glDisable(GL10.GL_TEXTURE_2D);
  }
  
  public void resize(GL10 gl, int screenWidth, int screenHeight, float fullCircleSize) {
    mArrowSizeFactor = ARROW_SIZE * Math.min(screenWidth, screenHeight);
    mArrowQuad = new TexturedQuad(mArrowTex,
                                  0, 0, 0,
                                  0.5f, 0, 0,
                                  0, 0.5f, 0);
    
    mFullCircleScaleFactor = fullCircleSize;
    mCircleSizeFactor = CIRCLE_SIZE * mFullCircleScaleFactor;
    mCircleQuad = new TexturedQuad(mCircleTex,
                                   0, 0, 0,
                                   0.5f, 0, 0,
                                   0, 0.5f, 0);
    
    mArrowOffset = mCircleSizeFactor + mArrowSizeFactor;
  }
  
  public void draw(GL10 gl, Vector3 lookDir, Vector3 upDir, SearchHelper searchHelper,
                   boolean nightVisionMode) {
    float lookPhi = MathUtils.acos(lookDir.y);
    float lookTheta = MathUtils.atan2(lookDir.z, lookDir.x);
    
    // Positive diffPhi means you need to look up.
    float diffPhi = lookPhi - mTargetPhi;
    
    // Positive diffTheta means you need to look right.
    float diffTheta = lookTheta - mTargetTheta;
    
    // diffTheta could potentially be in the range from (-2*Pi, 2*Pi), but we need it
    // in the range (-Pi, Pi).
    if (diffTheta > PI) {
      diffTheta -= TWO_PI;
    } else if (diffTheta < -PI) {
      diffTheta += TWO_PI;
    }
    
    // The image I'm using is an arrow pointing right, so an angle of 0 corresponds to that. 
    // This is why we're taking arctan(diffPhi / diffTheta), because diffTheta corresponds to
    // the amount we need to rotate in the xz plane and diffPhi in the up direction.
    float angle = MathUtils.atan2(diffPhi, diffTheta);
    
    // Need to add on the camera roll, which is the amount you need to rotate the vector (0, 1, 0)
    // about the look direction in order to get it in the same plane as the up direction.
    float roll = angleBetweenVectorsWithRespectToAxis(new Vector3(0, 1, 0), upDir, lookDir);
    
    angle += roll;
    
    // Distance is a normalized value of the distance.
    float distance = 1.0f / (1.414f * PI) *
        MathUtils.sqrt(diffTheta * diffTheta + diffPhi * diffPhi);
   
    gl.glEnable(GL10.GL_BLEND);
    gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
    
    gl.glPushMatrix();
    gl.glRotatef(angle * 180.0f / PI, 0, 0, -1);
    
    gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_BLEND);
    
    // 0 means the circle is not expanded at all.  1 means fully expanded.
    float expandFactor = searchHelper.getTransitionFactor();
    
    if (expandFactor == 0) {
      gl.glColor4x(FixedPoint.ONE, FixedPoint.ONE, FixedPoint.ONE, FixedPoint.ONE);
      
      float redFactor, blueFactor;
      if (nightVisionMode) {
        redFactor = 0.6f;
        blueFactor = 0;
      } else {
        redFactor = 1.0f - distance;
        blueFactor = distance;
      }
            
      gl.glTexEnvfv(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_COLOR, 
                    new float[] {redFactor, 0.0f, blueFactor, 0.0f}, 0);
      
      gl.glPushMatrix();
      float circleScale = mCircleSizeFactor;
      gl.glScalef(circleScale, circleScale, circleScale);
      mCircleQuad.draw(gl);
      gl.glPopMatrix();
    
      gl.glPushMatrix();
      float arrowScale = mArrowSizeFactor;
      gl.glTranslatef(mArrowOffset * 0.5f, 0, 0);
      gl.glScalef(arrowScale, arrowScale, arrowScale);
      mArrowQuad.draw(gl);
      gl.glPopMatrix();
    } else {
      gl.glColor4x(FixedPoint.ONE, FixedPoint.ONE, FixedPoint.ONE, 
                   FixedPoint.floatToFixedPoint(0.7f));
      
      gl.glTexEnvfv(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_COLOR, 
          new float[] {1, nightVisionMode ? 0 : 0.5f, 0, 0.0f}, 0);
      
      gl.glPushMatrix();
      float circleScale = mFullCircleScaleFactor * expandFactor + 
          mCircleSizeFactor * (1 - expandFactor);
      gl.glScalef(circleScale, circleScale, circleScale);
      mCircleQuad.draw(gl);
      gl.glPopMatrix();
    }
    gl.glPopMatrix();
    
    gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);
    
    gl.glDisable(GL10.GL_BLEND);
  }
  
  public void setTarget(Vector3 position) {
    position = position.normalizedCopy();
    mTargetPhi = MathUtils.acos(position.y);
    mTargetTheta = MathUtils.atan2(position.z, position.x);
  }
  
  // Given vectors v1 and v2, and an axis, this function returns the angle which you must rotate v1
  // by in order for it to be in the same plane as v2 and axis.  Assumes that all vectors are unit
  // vectors and v2 and axis are perpendicular.
  private static float angleBetweenVectorsWithRespectToAxis(Vector3 v1, Vector3 v2, Vector3 axis) {
    // Make v1 perpendicular to axis.  We want an orthonormal basis for the plane perpendicular
    // to axis.  After rotating v1, the projection of v1 and v2 into this plane should be equal.
    Vector3 v1proj = v1.minus(v1.projectOnto(axis));
    v1proj = v1proj.normalizedCopy();
    
    // Get the vector perpendicular to the one you're rotating and the axis.  Since axis and v1proj
    // are orthonormal, this one must be a unit vector perpendicular to all three.
    Vector3 perp = axis.times(v1proj);
    
    // v2 is perpendicular to axis, so therefore it's already in the same plane as v1proj perp.
    float cosAngle = v1proj.dot(v2);
    float sinAngle = -perp.dot(v2);
    
    return MathUtils.atan2(sinAngle, cosAngle);
  }
}
