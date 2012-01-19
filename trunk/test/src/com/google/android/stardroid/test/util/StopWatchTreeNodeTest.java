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
import com.google.android.stardroid.base.Provider;
import com.google.android.stardroid.util.StopWatch;
import com.google.android.stardroid.util.StopWatchImpl;
import com.google.android.stardroid.util.StopWatchTreeNode;

import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

/**
 * Unit tests for the {@link StopWatchTreeNode} class.
 *
 * @author Brent Bryan
 */
public class StopWatchTreeNodeTest extends TestCase {
  private StopWatchTreeNode node = new StopWatchTreeNode(StopWatchImpl.getProvider(), "foo");

  /**
   * Adds two children and a grandchild to the given root node and returns a
   * {@link List} of all five nodes.
   */
  private List<StopWatchTreeNode> addChildren(StopWatchTreeNode root) {
    List<StopWatchTreeNode> nodes = new ArrayList<StopWatchTreeNode>();
    nodes.add(root);
    nodes.add(root.getChild("child1"));
    nodes.add(root.getChild("child2"));
    nodes.add(root.getChild("child1").getChild("grandchild1"));
    nodes.add(root.getChild("child1").getChild("grandchild2"));
    return nodes;
  }

  public void testGetName() {
    assertEquals("foo", node.getName());
  }

  public void testGetStopWatch() {
    StopWatch watch = new StopWatchImpl();
    @SuppressWarnings("unchecked")
    Provider<StopWatch> watchProvider = EasyMock.createMock(Provider.class);
    EasyMock.expect(watchProvider.get()).andReturn(watch);

    EasyMock.replay(watchProvider);

    assertEquals(watch, new StopWatchTreeNode(watchProvider, "foo").getStopWatch());
    EasyMock.verify(watchProvider);
  }

  public void testGetChildren() {
    StopWatchTreeNode child1 = node.getChild("child1");
    StopWatchTreeNode child2 = node.getChild("child2");

    assertEquals(2, node.getNumChildren());
    assertEquals(Lists.asList(node.getChildren()), Lists.asList(child1, child2));
  }

  public void testGetChild_childPresent() {
    assertEquals(0, node.getNumChildren());

    StopWatchTreeNode child = node.getChild("child");
    assertEquals(1, node.getNumChildren());

    assertEquals(child, node.getChild("child"));
    assertEquals(1, node.getNumChildren());
  }

  public void testRemoveChild() {
    StopWatchTreeNode child1 = node.getChild("child1");
    StopWatchTreeNode child2 = node.getChild("child2");
    assertEquals(2, node.getNumChildren());

    node.removeChild("child1");
    assertEquals(1, node.getNumChildren());
    assertEquals(Lists.asList(node.getChildren()), Lists.asList(child2));

    node.removeChild("child2");
    assertEquals(0, node.getNumChildren());
    assertEquals(Lists.asList(node.getChildren()), Lists.asList());

    StopWatchTreeNode childA = node.getChild("child1");
    assertEquals(1, node.getNumChildren());
    assertEquals(Lists.asList(node.getChildren()), Lists.asList(childA));
    assertFalse(childA.equals(child1));
  }

  public void testRemoveChild_childNotPresent() {
    StopWatchTreeNode child1 = node.getChild("child1");
    StopWatchTreeNode child2 = node.getChild("child2");
    assertEquals(2, node.getNumChildren());

    assertNull(node.removeChild("child3"));
  }

  public void testRemoveChild_childHasChildren() {
    StopWatchTreeNode child1 = node.getChild("child1");
    StopWatchTreeNode child2 = node.getChild("child2");
    StopWatchTreeNode grandchild1 = child1.getChild("grandchild1");
    StopWatchTreeNode grandchild2 = child1.getChild("grandchild2");
    assertEquals(2, node.getNumChildren());

    node.removeChild("child1");
    assertEquals(1, node.getNumChildren());

    StopWatchTreeNode childA = node.getChild("child1");
    StopWatchTreeNode grandchildA = childA.getChild("grandchild1");
    assertFalse(child1.equals(childA));
    assertFalse(grandchild1.equals(grandchildA));
  }

  public void testStart() {
    final List<StopWatch> watches = new ArrayList<StopWatch>();
    for (int i = 0; i < 5; i++) {
      StopWatch watch = EasyMock.createMock(StopWatch.class);
      if (i == 0) {
        EasyMock.expect(watch.start()).andReturn(watch);
      }
      watches.add(watch);
    }

    node = new StopWatchTreeNode(new ListStopWatchProvider(watches), "foo");
    addChildren(node);
    EasyMock.replay(watches.toArray());

    assertEquals(node, node.start());
    EasyMock.verify(watches.toArray());
  }

  public void testStop() {
    final List<StopWatch> watches = new ArrayList<StopWatch>();
    for (int i = 0; i < 5; i++) {
      StopWatch watch = EasyMock.createMock(StopWatch.class);
      EasyMock.expect(watch.stop()).andReturn(watch);
      watches.add(watch);
    }

    node = new StopWatchTreeNode(new ListStopWatchProvider(watches), "foo");
    addChildren(node);

    EasyMock.replay(watches.toArray());

    assertEquals(node, node.stop());
    EasyMock.verify(watches.toArray());
  }

  public void testReset() {
    final List<StopWatch> watches = new ArrayList<StopWatch>();
    for (int i = 0; i < 5; i++) {
      StopWatch watch = EasyMock.createMock(StopWatch.class);
      EasyMock.expect(watch.clear()).andReturn(watch);
      watches.add(watch);
    }

    node = new StopWatchTreeNode(new ListStopWatchProvider(watches), "foo");
    List<StopWatchTreeNode> nodes = addChildren(node);
    assertEquals(2, node.getNumChildren());

    EasyMock.replay(watches.toArray());
    assertEquals(node, node.reset());

    for (StopWatchTreeNode n : nodes) {
      assertEquals(0, n.getNumChildren());
    }

    EasyMock.verify(watches.toArray());
  }
}
