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

import com.google.android.stardroid.util.TimingTreeNode;

import junit.framework.TestCase;

/**
 * Unit tests for the {@link TimingTreeNode} class.
 *
 * @author Brent Bryan
 */
public class TimingTreeNodeTest extends TestCase {
  public void testToString() {
    TimingTreeNode grandchild1 = new TimingTreeNode(2, "grandchild1", 100);
    TimingTreeNode grandchild2 = new TimingTreeNode(2, "grandchild2", 150);
    TimingTreeNode child1 = new TimingTreeNode(1, "child1", 400, grandchild1, grandchild2);
    TimingTreeNode child2 = new TimingTreeNode(1, "child2", 500);
    TimingTreeNode root = new TimingTreeNode(0, "root", 1000, child2, child1);

    String expected =
      "root: 00m 01s 000ms   (Parent: 100.00%, Total: 100.00%)\n" +
      "  child1: 00m 00s 400ms   (Parent: 40.00%, Total: 40.00%)\n" +
      "    grandchild1: 00m 00s 100ms   (Parent: 25.00%, Total: 10.00%)\n" +
      "    grandchild2: 00m 00s 150ms   (Parent: 37.50%, Total: 15.00%)\n" +
      "  child2: 00m 00s 500ms   (Parent: 50.00%, Total: 50.00%)\n";
    assertEquals(expected, root.toString());
  }

  public void testEquals() {
    TimingTreeNode node1 = new TimingTreeNode(3, "node1", 10);
    TimingTreeNode node2 = new TimingTreeNode(3, "node2", 20);
    TimingTreeNode node3 = new TimingTreeNode(3, "node3", 30);

    ImmutableEqualsTester.of(TimingTreeNode.class)
        .defaultArgs(2, "parent1", 100L, new TimingTreeNode[] {node1, node2})
        .alternativeArgs(3, "parent2", 200L, new TimingTreeNode[] {node3, node3})
        .testEquals();
  }
}
