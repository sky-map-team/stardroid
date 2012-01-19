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

package com.google.android.stardroid.util;

import com.google.android.stardroid.base.Nullable;
import com.google.android.stardroid.base.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Class representing a single, immutable node in the {@link TimingTree}. Each
 * node contains the name and elapsed time of the watch at the corresponding
 * node of the {@link StopWatchTree}, as well as references to its children.
 *
 * @author Brent Bryan
 */
public class TimingTreeNode {
  public final int level;
  public final String name;
  public final long elapsedTimeMs;
  public final List<TimingTreeNode> children;

  @VisibleForTesting
  public TimingTreeNode(int level, String name, long elapsedTimeMs, TimingTreeNode... children) {
    this(level, name, elapsedTimeMs, Arrays.asList(children));
  }

  @VisibleForTesting
  TimingTreeNode(int level, String name, long elapsedTimeMs,
      @Nullable List<TimingTreeNode> children) {
    this.level = level;
    this.name = name;
    this.elapsedTimeMs = elapsedTimeMs;
    this.children = (children == null) ? new ArrayList<TimingTreeNode>() : children;
    Collections.sort(children, new Comparator<TimingTreeNode>() {
      @Override
      public int compare(TimingTreeNode obj1, TimingTreeNode obj2) {
        return obj1.name.compareTo(obj2.name);
      }
    });
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    toStringBuilder(sb, elapsedTimeMs, elapsedTimeMs);
    return sb.toString();
  }

  /**
   * Writes a String representation of this {@link TimingTreeNode} to the given
   * {@link StringBuilder}.
   */
  private void toStringBuilder(StringBuilder sb, long parentElapsedTimeMs,
      long totalElapsedTimeMs) {
    for (int i = 0; i < level; i++) {
      sb.append("  ");
    }
    sb.append(String.format("%s: %s   (Parent: %5.2f%%, Total: %5.2f%%)\n", name, StopWatchImpl
        .formatTime(elapsedTimeMs), 100.0 * elapsedTimeMs / parentElapsedTimeMs, 100.0
        * elapsedTimeMs / totalElapsedTimeMs));

    for (TimingTreeNode child : children) {
      child.toStringBuilder(sb, elapsedTimeMs, totalElapsedTimeMs);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj instanceof TimingTreeNode) {
      TimingTreeNode that = (TimingTreeNode) obj;
      return this.level == that.level && this.name.equals(that.name)
          && this.elapsedTimeMs == that.elapsedTimeMs && this.children.equals(that.children);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(new Object[] {level, name, elapsedTimeMs, children});
  }
}

