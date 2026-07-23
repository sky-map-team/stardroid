package com.google.android.stardroid.renderer.util;

import java.util.ArrayList;
import java.util.List;

/** Resolves overlapping screen-space label bounds by moving later labels downward. */
public final class LabelCollisionResolver {
  private static final int DEFAULT_MAX_ITERATIONS = 10;

  private LabelCollisionResolver() {}

  public static List<LabelPosition> resolveCollisions(List<LabelPosition> labels) {
    return resolveCollisions(labels, DEFAULT_MAX_ITERATIONS);
  }

  public static List<LabelPosition> resolveCollisions(
      List<LabelPosition> labels, int maxIterations) {
    List<LabelPosition> resolvedLabels = new ArrayList<>(labels.size());
    for (LabelPosition label : labels) {
      LabelPosition resolvedLabel = label;
      int iterations = 0;
      while (iterations < maxIterations && overlapsAny(resolvedLabel, resolvedLabels)) {
        resolvedLabel =
            new LabelPosition(
                resolvedLabel.getX(),
                resolvedLabel.getY() - resolvedLabel.getHeight(),
                resolvedLabel.getWidth(),
                resolvedLabel.getHeight());
        iterations++;
      }
      resolvedLabels.add(resolvedLabel);
    }
    return resolvedLabels;
  }

  private static boolean overlapsAny(LabelPosition label, List<LabelPosition> placedLabels) {
    for (LabelPosition placedLabel : placedLabels) {
      if (overlaps(label, placedLabel)) {
        return true;
      }
    }
    return false;
  }

  private static boolean overlaps(LabelPosition first, LabelPosition second) {
    float firstHalfWidth = first.getWidth() * 0.5f;
    float firstHalfHeight = first.getHeight() * 0.5f;
    float secondHalfWidth = second.getWidth() * 0.5f;
    float secondHalfHeight = second.getHeight() * 0.5f;
    return first.getX() - firstHalfWidth < second.getX() + secondHalfWidth
        && first.getX() + firstHalfWidth > second.getX() - secondHalfWidth
        && first.getY() - firstHalfHeight < second.getY() + secondHalfHeight
        && first.getY() + firstHalfHeight > second.getY() - secondHalfHeight;
  }

  /** Screen-space center point and dimensions for a label. */
  public static final class LabelPosition {
    private final float x;
    private final float y;
    private final float width;
    private final float height;

    public LabelPosition(float x, float y, float width, float height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }

    public float getX() {
      return x;
    }

    public float getY() {
      return y;
    }

    public float getWidth() {
      return width;
    }

    public float getHeight() {
      return height;
    }
  }
}
