package com.google.android.stardroid.renderables;

import android.graphics.Color;

import com.google.android.stardroid.math.Vector3;

import java.util.List;

/**
 * A filled, additively-blended gradient mesh, used to render the soft glow just below the horizon.
 *
 * <p>The glow is described by a series of concentric "rings" of vertices: ring 0 lies exactly on
 * the horizon, and each subsequent ring is tilted a little further toward the nadir. Each ring has
 * an associated color whose alpha sets the glow intensity at that depth (peak near the horizon,
 * fading to zero at the deepest ring). The renderer fills the bands between consecutive rings and
 * interpolates the per-vertex color across each band, producing a single smooth gradient rather
 * than a stack of discrete translucent strips.
 *
 * <p>Drawn additively so the glow brightens whatever is behind it — black sky or the blue twilight
 * gradient alike — instead of blending toward the background and washing out.
 */
public class HorizonGlowPrimitive extends AbstractPrimitive {

  /** Concentric vertex loops, outermost (horizon) first. All loops must be the same length. */
  public final List<List<Vector3>> rings;

  /** ARGB color for each ring; must have the same length as {@link #rings}. */
  public final int[] ringColors;

  public HorizonGlowPrimitive(List<List<Vector3>> rings, int[] ringColors) {
    super(ringColors != null && ringColors.length > 0 ? ringColors[0] : Color.WHITE);
    if (rings == null || ringColors == null || rings.size() != ringColors.length) {
      throw new IllegalArgumentException(
          "rings and ringColors must be non-null and the same length");
    }
    // The renderer treats every ring as having the same vertex count (it indexes all rings by
    // ring 0's length), so enforce that invariant up front rather than letting ragged input
    // corrupt the mesh or throw deep in the render loop.
    if (!rings.isEmpty()) {
      int ringLength = rings.get(0) != null ? rings.get(0).size() : 0;
      for (List<Vector3> ring : rings) {
        if (ring == null || ring.size() != ringLength) {
          throw new IllegalArgumentException("All rings must be non-null and the same length");
        }
      }
    }
    this.rings = rings;
    this.ringColors = ringColors;
  }
}
