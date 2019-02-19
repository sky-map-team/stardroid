// Copyright 2010 Google Inc.
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

package com.google.android.stardroid.test.util;

import com.google.android.stardroid.base.Lists;
import com.google.android.stardroid.base.PreconditionException;
import com.google.android.stardroid.util.TimingTree;
import com.google.android.stardroid.util.TimingTreeNode;

import junit.framework.TestCase;

/**
 * Unit tests for the {@link TimingTree} class.
 *
 * @author Brent Bryan
 */
public class TimingTreeTest extends TestCase {
  private TimingTreeNode grandchild1 = new TimingTreeNode(2, "child1", 100);
  private TimingTreeNode grandchild2 = new TimingTreeNode(2, "child2", 150);
  private TimingTreeNode child1 = new TimingTreeNode(1, "child1", 400, grandchild1, grandchild2);
  private TimingTreeNode child2 = new TimingTreeNode(1, "child2", 500);
  private TimingTreeNode root = new TimingTreeNode(0, "root", 1000, child2, child1);
  private TimingTree tree = new TimingTree(root);

  public void testConstructor() {
    try {
      new TimingTree(null);
      fail("Root node cannot be null");
    } catch (PreconditionException e) {
      // root not cannot be null.
    }
  }

  public void testGetRoot() {
    assertEquals(root, tree.getRoot());
  }

  public void testGetNamedNodes_noNodeWithName() {
    assertEquals(Lists.<TimingTreeNode>asList(), tree.getNamedNodes("foo"));
  }

  public void testGetNamedNodes_nodeWithName() {
    assertEquals(Lists.asList(root), tree.getNamedNodes("root"));
  }

  public void testGetNamedNodes_multiNodesWithName() {
    assertEquals(Lists.asList(child1, grandchild1), tree.getNamedNodes("child1"));
  }

  public void testToString() {
    assertEquals(root.toString(), tree.toString());
  }

  public void testEquals() {
    ImmutableEqualsTester.of(TimingTree.class)
        .defaultArgs(root)
        .alternativeArgs(child1)
        .testEquals();
  }
}
