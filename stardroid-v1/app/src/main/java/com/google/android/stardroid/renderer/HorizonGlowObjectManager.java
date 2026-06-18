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

    // Count vertices and band quads across all primitives. A primitive needs at least two
    // rings of at least two vertices each to form a band; anything less is skipped (the same
    // skip is mirrored in the build loop below so vertex counts and indices stay aligned).
    int numVertices = 0;
    int numQuads = 0;
    for (HorizonGlowPrimitive glow : glows) {
      if (!isDrawable(glow)) {
        continue;
      }
      int rings = glow.rings.size();
      int ringLength = glow.rings.get(0).size();
      numVertices += rings * ringLength;
      numQuads += (rings - 1) * (ringLength - 1);
    }

    // Vertices are indexed with signed shorts; bail out rather than overflow into corruption.
    if (numVertices > Short.MAX_VALUE) {
      vertexBuffer.reset(0);
      colorBuffer.reset(0);
      indexBuffer.reset(0);
      return;
    }

    vertexBuffer.reset(numVertices);
    colorBuffer.reset(numVertices);
    indexBuffer.reset(6 * numQuads);

    short vertexIndex = 0;
    for (HorizonGlowPrimitive glow : glows) {
      if (!isDrawable(glow)) {
        continue;
      }
      List<List<Vector3>> rings = glow.rings;
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

  /** A glow needs at least two rings of at least two vertices each to form a band of quads. */
  private static boolean isDrawable(HorizonGlowPrimitive glow) {
    List<List<Vector3>> rings = glow.rings;
    return rings != null && rings.size() >= 2 && rings.get(0) != null && rings.get(0).size() >= 2;
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
