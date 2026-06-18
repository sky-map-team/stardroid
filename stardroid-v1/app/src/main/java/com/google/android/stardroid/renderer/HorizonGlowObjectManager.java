package com.google.android.stardroid.renderer;

import com.google.android.stardroid.math.Vector3;
import com.google.android.stardroid.renderables.HorizonGlowPrimitive;
import com.google.android.stardroid.renderer.util.IndexBuffer;
import com.google.android.stardroid.renderer.util.NightVisionColorBuffer;
import com.google.android.stardroid.renderer.util.TextureManager;
import com.google.android.stardroid.renderer.util.VertexBuffer;

import java.util.EnumSet;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

/**
 * Renders {@link HorizonGlowPrimitive}s as a smooth, additively-blended gradient mesh.
 *
 * <p>Each primitive supplies a set of concentric vertex rings (ring 0 on the horizon, the rest
 * tilted toward the nadir) plus a color per ring. This manager fills the bands between consecutive
 * rings as triangle pairs and lets OpenGL's smooth shading interpolate the per-vertex colors —
 * including their alpha — across each band, giving a continuous gradient with no visible seams.
 *
 * <p>Unlike {@link PolyLineObjectManager}, the mesh is drawn with additive blending
 * ({@code GL_SRC_ALPHA, GL_ONE}) so the glow always adds light to the background instead of
 * blending toward it, and with no texture and no back-face culling (the band is viewed edge-on
 * from inside the celestial sphere, so both windings must render).
 */
public class HorizonGlowObjectManager extends RendererObjectManager {
  private final VertexBuffer vertexBuffer = new VertexBuffer(true);
  private final NightVisionColorBuffer colorBuffer = new NightVisionColorBuffer(true);
  private final IndexBuffer indexBuffer = new IndexBuffer(true);

  public HorizonGlowObjectManager(int layer, TextureManager textureManager) {
    super(layer, textureManager);
  }

  public void updateObjects(List<HorizonGlowPrimitive> glows,
                            EnumSet<UpdateType> updateType) {
    if (!updateType.contains(UpdateType.Reset)
        && !updateType.contains(UpdateType.UpdatePositions)) {
      return;
    }

    // Count vertices and band quads across all primitives.
    int numVertices = 0;
    int numQuads = 0;
    for (HorizonGlowPrimitive glow : glows) {
      List<List<Vector3>> rings = glow.rings;
      if (rings.size() < 2) {
        continue;
      }
      int ringLength = rings.get(0).size();
      numVertices += rings.size() * ringLength;
      numQuads += (rings.size() - 1) * (ringLength - 1);
    }

    vertexBuffer.reset(numVertices);
    colorBuffer.reset(numVertices);
    indexBuffer.reset(6 * numQuads);

    short vertexIndex = 0;
    for (HorizonGlowPrimitive glow : glows) {
      List<List<Vector3>> rings = glow.rings;
      if (rings.size() < 2) {
        continue;
      }
      int ringLength = rings.get(0).size();

      // Vertices, ring by ring, each ring painted in its own color.
      for (int ring = 0; ring < rings.size(); ring++) {
        List<Vector3> verts = rings.get(ring);
        int color = glow.ringColors[ring];
        for (int i = 0; i < ringLength; i++) {
          vertexBuffer.addPoint(verts.get(i));
          colorBuffer.addColor(color);
        }
      }

      // Indices: two triangles per quad between consecutive rings.
      for (int ring = 0; ring < rings.size() - 1; ring++) {
        short topRowStart = (short) (vertexIndex + ring * ringLength);
        short bottomRowStart = (short) (topRowStart + ringLength);
        for (int i = 0; i < ringLength - 1; i++) {
          short topLeft = (short) (topRowStart + i);
          short topRight = (short) (topLeft + 1);
          short bottomLeft = (short) (bottomRowStart + i);
          short bottomRight = (short) (bottomLeft + 1);

          indexBuffer.addIndex(topLeft);
          indexBuffer.addIndex(bottomLeft);
          indexBuffer.addIndex(bottomRight);

          indexBuffer.addIndex(topLeft);
          indexBuffer.addIndex(bottomRight);
          indexBuffer.addIndex(topRight);
        }
      }
      vertexIndex += rings.size() * ringLength;
    }
  }

  @Override
  public void reload(GL10 gl, boolean fullReload) {
    vertexBuffer.reload();
    colorBuffer.reload();
    indexBuffer.reload();
  }

  @Override
  protected void drawInternal(GL10 gl) {
    if (indexBuffer.size() == 0) {
      return;
    }

    gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
    gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
    gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

    gl.glDisable(GL10.GL_TEXTURE_2D);
    gl.glDisable(GL10.GL_CULL_FACE);
    gl.glShadeModel(GL10.GL_SMOOTH);

    gl.glEnable(GL10.GL_BLEND);
    // Additive: the glow adds light to whatever is behind it.
    gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE);

    vertexBuffer.set(gl);
    colorBuffer.set(gl, getRenderState().getNightVisionMode());

    indexBuffer.draw(gl, GL10.GL_TRIANGLES);

    gl.glDisable(GL10.GL_BLEND);
    gl.glEnable(GL10.GL_CULL_FACE);
  }
}
