package com.google.android.stardroid.renderer.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.stardroid.renderer.util.LabelCollisionResolver.LabelPosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class LabelCollisionResolverTest {
  @Test
  public void resolveCollisions_noOverlap_keepsOriginalPositions() {
    List<LabelPosition> labels = Arrays.asList(label(10, 20, 30, 10), label(100, 200, 20, 20));

    List<LabelPosition> resolved = LabelCollisionResolver.resolveCollisions(labels);

    assertPosition(resolved.get(0), 10, 20);
    assertPosition(resolved.get(1), 100, 200);
  }

  @Test
  public void resolveCollisions_pairOverlap_movesSecondLabelDown() {
    List<LabelPosition> labels = Arrays.asList(label(10, 20, 30, 10), label(10, 20, 30, 10));

    List<LabelPosition> resolved = LabelCollisionResolver.resolveCollisions(labels);

    assertPosition(resolved.get(0), 10, 20);
    assertThat(resolved.get(1).getY()).isAtMost(10);
    assertThat(overlaps(resolved.get(0), resolved.get(1))).isFalse();
  }

  @Test
  public void resolveCollisions_tightCluster_resolvesEveryPair() {
    List<LabelPosition> labels = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      labels.add(label(10, 20, 30, 10));
    }

    List<LabelPosition> resolved = LabelCollisionResolver.resolveCollisions(labels);

    for (int i = 0; i < resolved.size(); i++) {
      for (int j = i + 1; j < resolved.size(); j++) {
        assertThat(overlaps(resolved.get(i), resolved.get(j))).isFalse();
      }
    }
  }

  @Test
  public void resolveCollisions_emptyAndSingleLabel_areUnchanged() {
    assertThat(LabelCollisionResolver.resolveCollisions(Collections.emptyList())).isEmpty();

    List<LabelPosition> resolved =
        LabelCollisionResolver.resolveCollisions(Collections.singletonList(label(10, 20, 30, 10)));

    assertThat(resolved).hasSize(1);
    assertPosition(resolved.get(0), 10, 20);
  }

  @Test
  public void resolveCollisions_iterationLimit_stopsFurtherOffsets() {
    List<LabelPosition> labels = Arrays.asList(label(10, 20, 30, 100), label(10, 20, 30, 10));

    List<LabelPosition> resolved = LabelCollisionResolver.resolveCollisions(labels, 2);

    assertPosition(resolved.get(1), 10, 0);
    assertThat(overlaps(resolved.get(0), resolved.get(1))).isTrue();
  }

  private static LabelPosition label(float x, float y, float width, float height) {
    return new LabelPosition(x, y, width, height);
  }

  private static void assertPosition(LabelPosition label, float x, float y) {
    assertThat(label.getX()).isEqualTo(x);
    assertThat(label.getY()).isEqualTo(y);
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
}
