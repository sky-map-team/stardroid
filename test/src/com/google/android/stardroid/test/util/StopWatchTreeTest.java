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

import com.google.android.stardroid.base.PreconditionException;
import com.google.android.stardroid.util.StopWatch;
import com.google.android.stardroid.util.StopWatchImpl;
import com.google.android.stardroid.util.StopWatchTree;
import com.google.android.stardroid.util.StopWatchTreeNode;
import com.google.android.stardroid.util.TimingTree;
import com.google.android.stardroid.util.TimingTreeNode;

import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

/**
 * Unit tests for the {@link StopWatchTree} class.
 *
 * @author Brent Bryan
 */
public class StopWatchTreeTest extends TestCase {
  private StopWatchTree tree = new StopWatchTree(StopWatchImpl.getProvider());

  /**
   * Adds two children and a grandchild to the root node and returns a
   * {@link List} of all four nodes.
   */
  private List<StopWatchTreeNode> addChildren() {
    List<StopWatchTreeNode> nodes = new ArrayList<StopWatchTreeNode>();
    nodes.add(tree.getRoot());
    nodes.add(tree.getRoot().getChild("child1"));
    nodes.add(tree.getRoot().getChild("child1").getChild("grandchild1"));
    nodes.add(tree.getRoot().getChild("child2"));
    return nodes;
  }

  public void testGetRoot() {
    assertNotNull(tree.getRoot());
    assertEquals("Total Elapsed Time", tree.getRoot().getName());
  }

  public void testIsRunning() {
    addChildren();
    assertFalse(tree.isRunning());

    assertEquals(tree, tree.start());
    assertTrue(tree.isRunning());

    assertEquals(tree, tree.stop());
    assertFalse(tree.isRunning());

    assertEquals(tree, tree.start());
    assertTrue(tree.isRunning());

    assertEquals(tree, tree.reset());
    assertFalse(tree.isRunning());
  }

  public void testStart() {
    final List<StopWatch> watches = new ArrayList<StopWatch>();
    for (int i = 0; i < 4; i++) {
      StopWatch watch = EasyMock.createMock(StopWatch.class);
      if (i == 0) {
        EasyMock.expect(watch.isRunning()).andReturn(false);
        EasyMock.expect(watch.start()).andReturn(watch);
      }
      watches.add(watch);
    }

    tree = new StopWatchTree(new ListStopWatchProvider(watches));
    addChildren();
    EasyMock.replay(watches.toArray());

    assertEquals(tree, tree.start());
    EasyMock.verify(watches.toArray());
  }

  public void testStart_alreadyRunning() {
    addChildren();
    assertEquals(tree, tree.start());

    try {
      tree.start();
      fail("Cannot start a currently running tree.");
    } catch (PreconditionException e) {
      // Cannot start a currently running tree.
    }
  }

  public void testStop() {
    final List<StopWatch> watches = new ArrayList<StopWatch>();
    for (int i = 0; i < 4; i++) {
      StopWatch watch = EasyMock.createMock(StopWatch.class);
      if (i == 0) {
        EasyMock.expect(watch.isRunning()).andReturn(true);
      }
      EasyMock.expect(watch.stop()).andReturn(watch);
      watches.add(watch);
    }

    tree = new StopWatchTree(new ListStopWatchProvider(watches));
    addChildren();
    EasyMock.replay(watches.toArray());

    assertEquals(tree, tree.stop());
    EasyMock.verify(watches.toArray());
  }

  public void testStop_notRunning() {
    addChildren();

    try {
      tree.stop();
      fail("Cannot start a currently running tree.");
    } catch (PreconditionException e) {
      // Cannot start a currently running tree.
    }
  }

  public void testReset() {
    final List<StopWatch> watches = new ArrayList<StopWatch>();
    for (int i = 0; i < 4; i++) {
      StopWatch watch = EasyMock.createMock(StopWatch.class);
      EasyMock.expect(watch.clear()).andReturn(watch);
      watches.add(watch);
    }

    tree = new StopWatchTree(new ListStopWatchProvider(watches));
    StopWatchTreeNode root = tree.getRoot();
    List<StopWatchTreeNode> nodes = addChildren();
    EasyMock.replay(watches.toArray());

    assertEquals(tree, tree.reset());
    assertEquals(root, tree.getRoot());

    for (StopWatchTreeNode n : nodes) {
      assertEquals(0, n.getNumChildren());
    }

    EasyMock.verify(watches.toArray());
  }

  public void testGetCurrentTiming() {
    final List<StopWatch> watches = new ArrayList<StopWatch>();
    for (int i = 0; i < 4; i++) {
      StopWatch watch = EasyMock.createMock(StopWatch.class);
      EasyMock.expect(watch.getElapsedTime()).andReturn(50L + i * 100L);
      watches.add(watch);
    }

    TimingTree expected = new TimingTree(
        new TimingTreeNode(0, "Total Elapsed Time", 50L,
            new TimingTreeNode(1, "child1", 150L,
                new TimingTreeNode(2, "grandchild1", 250L)),
            new TimingTreeNode(1, "child2", 350L)));

    tree = new StopWatchTree(new ListStopWatchProvider(watches));
    List<StopWatchTreeNode> nodes = addChildren();
    EasyMock.replay(watches.toArray());

    TimingTree observed = tree.getCurrentTiming();
    assertEquals(expected, observed);

    EasyMock.verify(watches.toArray());
  }
}
