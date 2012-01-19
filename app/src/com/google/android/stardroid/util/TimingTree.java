// Copyright 2010 Google Inc.
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

import com.google.android.stardroid.base.Preconditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * An immutable, hierarchical view of recorded times. Typically
 * {@link TimingTree}s are generated from the {@link StopWatchImpl}s in
 * {@link StopWatchTree}s. in a {@link StopWatchTree}.
 *
 * @author Brent Bryan
 */
public class TimingTree {
  private final TimingTreeNode root;
  private final HashMap<String, List<TimingTreeNode>> nodeNamesMap =
      new HashMap<String, List<TimingTreeNode>>();

  public TimingTree(TimingTreeNode root) {
    this.root = Preconditions.checkNotNull(root);
    insertNodeIntoNodeNameMap(root);
  }

  /**
   * Inserts the given (immutable) TimingTreeNode into the name -> list of nodes
   * map. This allows us to subsequently reference all nodes with a given name.
   */
  private void insertNodeIntoNodeNameMap(TimingTreeNode node) {
    List<TimingTreeNode> list = nodeNamesMap.get(node.name);
    if (list == null) {
      list = new ArrayList<TimingTreeNode>();
      nodeNamesMap.put(node.name, list);
    }
    list.add(node);

    for (TimingTreeNode child : node.children) {
      if (child != null) {
        insertNodeIntoNodeNameMap(child);
      }
    }
  }

  /**
   * Returns the root node of this {@link TimingTree}.
   *
   * @return {@link TimingTreeNode} corresponding to the root of this
   *         {@link TimingTree}
   */
  public TimingTreeNode getRoot() {
    return root;
  }

  /**
   * Returns a (possibly empty) list of {@link TimingTreeNode}s in this
   * {@link TimingTree} which have the given name.
   */
  public List<TimingTreeNode> getNamedNodes(String name) {
    List<TimingTreeNode> result = nodeNamesMap.get(name);
    if (result == null) {
      return new ArrayList<TimingTreeNode>();
    }
    return result;
  }

  @Override
  public String toString() {
    return root.toString();
  }

  @Override
  public int hashCode() {
    return root.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj instanceof TimingTree) {
      TimingTree that = (TimingTree) obj;
      return this.root.equals(that.root);
    }
    return false;
  }
}
