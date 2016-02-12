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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.android.stardroid.base.Lists;
import com.google.android.stardroid.base.PreconditionException;
import com.google.android.stardroid.base.Provider;
import com.google.android.stardroid.util.StopWatch;
import com.google.android.stardroid.util.StopWatchTree;
import com.google.android.stardroid.util.StopWatchTreeNode;
import com.google.android.stardroid.util.StopWatchTreePath;
import com.google.android.stardroid.util.TimingTree;
import com.google.android.stardroid.util.TimingTreeNode;

import java.util.List;

import junit.framework.TestCase;

/**
 * Unit tests for the {@link StopWatchTreePath} class.
 *
 * @author Brent Bryan
 */
public class StopWatchTreePathTest extends TestCase {

  /**
   * Instance of a {@link Provider} of {@link StopWatchTree}'s used for
   * testing.  This class creates a new {@link StopWatchTree} which uses
   * the given watches when creating new {@link StopWatchTreeNode}s.
   */
  static class MyStopWatchTreeProvider implements Provider<StopWatchTree> {
    private final List<StopWatch> watches;

    public MyStopWatchTreeProvider(List<StopWatch> watches) {
      this.watches = watches;
    }

    @Override
    public StopWatchTree get() {
      return new StopWatchTree(new ListStopWatchProvider(watches));
    }
  }

  private List<StopWatch> getMockWatches() {
    // start tree
    StopWatch rootWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(false);
    expect(rootWatch.start()).andReturn(rootWatch);

    // add child1
    StopWatch child1Watch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1Watch.start()).andReturn(child1Watch);

    // add grandchild1
    StopWatch grandchildWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(grandchildWatch.start()).andReturn(grandchildWatch);

    // add child2
    expect(rootWatch.isRunning()).andReturn(true);
    expect(grandchildWatch.stop()).andReturn(grandchildWatch).anyTimes();
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1Watch.stop()).andReturn(child1Watch);

    StopWatch child2Watch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child2Watch.start()).andReturn(child2Watch);

    // back to root.
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child2Watch.stop()).andReturn(child2Watch);

    // get timing tree
    expect(rootWatch.getElapsedTime()).andReturn(400L);
    expect(child1Watch.getElapsedTime()).andReturn(200L);
    expect(grandchildWatch.getElapsedTime()).andReturn(100L);
    expect(child2Watch.getElapsedTime()).andReturn(150L);

    return Lists.asList(rootWatch, child1Watch, grandchildWatch, child2Watch);
  }

  private TimingTree getTimingTreeForMockWatches() {
    return new TimingTree(
        new TimingTreeNode(0, "Total Elapsed Time", 400L,
            new TimingTreeNode(1, "child1", 200L,
                new TimingTreeNode(2, "grandchild", 100L)),
            new TimingTreeNode(1, "child2", 150L)));
  }


  private StopWatchTreePath getTreePathForPopAtRootTest() {
    // start tree
    StopWatch rootWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(false);
    expect(rootWatch.start()).andReturn(rootWatch);

    // pop root
    expect(rootWatch.isRunning()).andReturn(true);

    List<StopWatch> watches = Lists.asList(rootWatch);
    replay(watches.toArray());

    return new StopWatchTreePath(new MyStopWatchTreeProvider(watches));
  }

  private StopWatchTreePath getTreePathForPopNotRunningTest() {
    // start tree
    StopWatch rootWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(false);
    expect(rootWatch.start()).andReturn(rootWatch);

    // add child1
    StopWatch childWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(childWatch.start()).andReturn(childWatch);

    // pop child1
    expect(rootWatch.isRunning()).andReturn(false);

    List<StopWatch> watches = Lists.asList(rootWatch, childWatch);
    replay(watches.toArray());

    return new StopWatchTreePath(new MyStopWatchTreeProvider(watches));
  }

  public void testReset() {
    // start tree
    StopWatch rootWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(false);
    expect(rootWatch.start()).andReturn(rootWatch);

    // add child1
    StopWatch child1Watch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1Watch.start()).andReturn(child1Watch);

    // add grandchild1
    StopWatch grandchildWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(grandchildWatch.start()).andReturn(grandchildWatch);

    // add child2
    expect(rootWatch.isRunning()).andReturn(true);
    expect(grandchildWatch.stop()).andReturn(grandchildWatch).anyTimes();
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1Watch.stop()).andReturn(child1Watch);

    StopWatch child2Watch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child2Watch.start()).andReturn(child2Watch);

    // reset
    // Note: grandchild has been removed, so it doesn't need to be cleared
    expect(rootWatch.clear()).andReturn(rootWatch);
    expect(child1Watch.clear()).andReturn(child1Watch);
    expect(child2Watch.clear()).andReturn(child2Watch);
    expect(rootWatch.isRunning()).andReturn(false);
    expect(rootWatch.start()).andReturn(rootWatch);

    // get timing tree
    expect(rootWatch.getElapsedTime()).andReturn(400L);

    List<StopWatch> watches = Lists.asList(rootWatch, child1Watch, grandchildWatch, child2Watch);
    replay(watches.toArray());

    StopWatchTreePath path = new StopWatchTreePath(new MyStopWatchTreeProvider(watches));
    StopWatchTreeNode root = path.getCurrentNode();
    StopWatchTreeNode child1 = path.push("child1").getCurrentNode();
    StopWatchTreeNode grandchild = path.push("grandchild").getCurrentNode();
    StopWatchTreeNode child2 = path.popAndRemove().pop().push("child2").getCurrentNode();

    assertEquals(child2, path.getCurrentNode());
    assertEquals(path, path.reset());

    assertEquals(root, path.getCurrentNode());
    assertEquals(0, root.getNumChildren());
    assertEquals(0, child1.getNumChildren());
    assertEquals(0, child2.getNumChildren());
    assertEquals(0, grandchild.getNumChildren());

    TimingTree expected = new TimingTree(new TimingTreeNode(0, "Total Elapsed Time", 400L));
    assertEquals(expected, path.getCurrentTiming());

    verify(watches.toArray());
  }

  public void testPush() {
    // start tree
    StopWatch rootWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(false);
    expect(rootWatch.start()).andReturn(rootWatch);

    // add child1
    StopWatch childWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(childWatch.start()).andReturn(childWatch);

    // add grandchild1
    StopWatch grandchildWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(grandchildWatch.start()).andReturn(grandchildWatch);

    // get timing tree
    expect(rootWatch.getElapsedTime()).andReturn(400L);
    expect(childWatch.getElapsedTime()).andReturn(200L);
    expect(grandchildWatch.getElapsedTime()).andReturn(100L);

    List<StopWatch> watches = Lists.asList(rootWatch, childWatch, grandchildWatch);
    replay(watches.toArray());

    StopWatchTreePath path = new StopWatchTreePath(new MyStopWatchTreeProvider(watches));
    StopWatchTreeNode root = path.getCurrentNode();
    StopWatchTreeNode child = path.push("child").getCurrentNode();
    StopWatchTreeNode grandchild = path.push("grandchild").getCurrentNode();

    assertEquals(1, root.getNumChildren());
    assertEquals(child, Lists.asList(root.getChildren()).get(0));

    assertEquals(1, child.getNumChildren());
    assertEquals(grandchild, Lists.asList(child.getChildren()).get(0));

    assertEquals(0, grandchild.getNumChildren());

    TimingTree expected = new TimingTree(
        new TimingTreeNode(0, "Total Elapsed Time", 400L,
            new TimingTreeNode(1, "child", 200L,
                new TimingTreeNode(2, "grandchild", 100L))));

    assertEquals(expected, path.getCurrentTiming());
    verify(watches.toArray());
  }

  public void testPush_returnToNode() {
    // start tree
    StopWatch rootWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(false);
    expect(rootWatch.start()).andReturn(rootWatch);

    // add child1
    StopWatch child1Watch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1Watch.start()).andReturn(child1Watch);

    // add grandchild1
    StopWatch grandchildWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(grandchildWatch.start()).andReturn(grandchildWatch);

    // add child2
    expect(rootWatch.isRunning()).andReturn(true);
    expect(grandchildWatch.stop()).andReturn(grandchildWatch).anyTimes();
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1Watch.stop()).andReturn(child1Watch);

    StopWatch child2Watch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child2Watch.start()).andReturn(child2Watch);

    // back to root.
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child2Watch.stop()).andReturn(child2Watch);

    // back to child 1
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1Watch.start()).andReturn(child1Watch);

    // back to root.
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1Watch.stop()).andReturn(child1Watch);

    // get timing tree
    expect(rootWatch.getElapsedTime()).andReturn(400L);
    expect(child1Watch.getElapsedTime()).andReturn(200L);
    expect(grandchildWatch.getElapsedTime()).andReturn(100L);
    expect(child2Watch.getElapsedTime()).andReturn(150L);

    List<StopWatch> watches = Lists.asList(rootWatch, child1Watch, grandchildWatch, child2Watch);
    replay(watches.toArray());

    StopWatchTreePath path = new StopWatchTreePath(new MyStopWatchTreeProvider(watches));
    StopWatchTreeNode root = path.getCurrentNode();
    StopWatchTreeNode child1 = path.push("child1").getCurrentNode();
    StopWatchTreeNode grandchild = path.push("grandchild").getCurrentNode();
    StopWatchTreeNode child2 = path.pop().pop().push("child2").getCurrentNode();
    path.pop().push("child1").pop();

    assertEquals(root, path.getCurrentNode());

    List<StopWatchTreeNode> observedChildren = Lists.asList(root.getChildren());
    assertEquals(2, observedChildren.size());
    assertTrue(observedChildren.contains(child1));
    assertTrue(observedChildren.contains(child2));

    assertEquals(1, child1.getNumChildren());
    assertEquals(grandchild, Lists.asList(child1.getChildren()).get(0));

    assertEquals(0, grandchild.getNumChildren());
    assertEquals(0, child2.getNumChildren());

    assertEquals(getTimingTreeForMockWatches(), path.getCurrentTiming());
    verify(watches.toArray());
  }

  public void testPush_notRunning() {
    // start tree
    StopWatch rootWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(false);
    expect(rootWatch.start()).andReturn(rootWatch);

    // push child1
    expect(rootWatch.isRunning()).andReturn(false);

    List<StopWatch> watches = Lists.asList(rootWatch);
    replay(watches.toArray());

    StopWatchTreePath path = new StopWatchTreePath(new MyStopWatchTreeProvider(watches));

    try {
      path.push("child");
      fail("Cannot push an element onto a stopped tree.");
    } catch (PreconditionException e) {
      // cannot push an element onto a stopped tree
    }
  }

  public void testPop() {
    List<StopWatch> watches = getMockWatches();
    replay(watches.toArray());

    StopWatchTreePath path = new StopWatchTreePath(new MyStopWatchTreeProvider(watches));
    StopWatchTreeNode root = path.getCurrentNode();
    StopWatchTreeNode child1 = path.push("child1").getCurrentNode();
    StopWatchTreeNode grandchild = path.push("grandchild").getCurrentNode();
    StopWatchTreeNode child2 = path.pop().pop().push("child2").getCurrentNode();
    path.pop();

    assertEquals(root, path.getCurrentNode());

    List<StopWatchTreeNode> observedChildren = Lists.asList(root.getChildren());
    assertEquals(2, observedChildren.size());
    assertTrue(observedChildren.contains(child1));
    assertTrue(observedChildren.contains(child2));

    assertEquals(1, child1.getNumChildren());
    assertEquals(grandchild, Lists.asList(child1.getChildren()).get(0));

    assertEquals(0, grandchild.getNumChildren());
    assertEquals(0, child2.getNumChildren());

    assertEquals(getTimingTreeForMockWatches(), path.getCurrentTiming());
    verify(watches.toArray());
  }

  public void testPop_notRunning() {
    StopWatchTreePath path = getTreePathForPopNotRunningTest();

    try {
      path.pop();
      fail("Cannot push an element onto a stopped tree.");
    } catch (PreconditionException e) {
      // cannot push an element onto a stopped tree
    }
  }

  public void testPop_atRoot() {
    StopWatchTreePath path = getTreePathForPopAtRootTest();

    try {
      path.pop();
      fail("Cannot pop the root element from a tree.");
    } catch (PreconditionException e) {
      // Cannot pop the root element from a tree;
    }
  }

  public void testPopAndRemove() {
    List<StopWatch> watches = getMockWatches();
    replay(watches.toArray());

    StopWatchTreePath path = new StopWatchTreePath(new MyStopWatchTreeProvider(watches));
    StopWatchTreeNode root = path.getCurrentNode();
    StopWatchTreeNode child1 = path.push("child1").getCurrentNode();
    StopWatchTreeNode grandchild = path.push("grandchild").getCurrentNode();
    StopWatchTreeNode child2 = path.popAndRemove().pop().push("child2").getCurrentNode();
    path.popAndRemove();

    assertEquals(root, path.getCurrentNode());
    assertEquals(1, root.getNumChildren());
    assertEquals(child1, Lists.asList(root.getChildren()).get(0));

    assertEquals(0, child1.getNumChildren());
    assertEquals(0, grandchild.getNumChildren());
    assertEquals(0, child2.getNumChildren());

    assertEquals(getTimingTreeForMockWatches(), path.getCurrentTiming());
    verify(watches.toArray());
  }

  public void testPush_toPopAndRemovedNode() {
    // start tree
    StopWatch rootWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(false);
    expect(rootWatch.start()).andReturn(rootWatch);

    // add child1
    StopWatch child1Watch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1Watch.start()).andReturn(child1Watch);

    // add grandchild1
    StopWatch grandchildWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(grandchildWatch.start()).andReturn(grandchildWatch);

    // add child2
    expect(rootWatch.isRunning()).andReturn(true);
    expect(grandchildWatch.stop()).andReturn(grandchildWatch).anyTimes();
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1Watch.stop()).andReturn(child1Watch);

    StopWatch child2Watch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child2Watch.start()).andReturn(child2Watch);

    // back to root.
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child2Watch.stop()).andReturn(child2Watch);

    // add child1 again
    StopWatch child1bWatch = createMock(StopWatch.class);
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1bWatch.start()).andReturn(child1bWatch);

    // back to root.
    expect(rootWatch.isRunning()).andReturn(true);
    expect(child1bWatch.stop()).andReturn(child1bWatch);

    // get timing tree
    expect(rootWatch.getElapsedTime()).andReturn(400L);
    expect(child1Watch.getElapsedTime()).andReturn(200L);
    expect(grandchildWatch.getElapsedTime()).andReturn(100L);
    expect(child2Watch.getElapsedTime()).andReturn(150L);
    expect(child1bWatch.getElapsedTime()).andReturn(300L);

    List<StopWatch> watches = Lists.asList(rootWatch, child1Watch, grandchildWatch,
        child2Watch, child1bWatch);
    replay(watches.toArray());

    StopWatchTreePath path = new StopWatchTreePath(new MyStopWatchTreeProvider(watches));
    StopWatchTreeNode root = path.getCurrentNode();
    StopWatchTreeNode child1 = path.push("child1").getCurrentNode();
    StopWatchTreeNode grandchild = path.push("grandchild").getCurrentNode();
    StopWatchTreeNode child2 = path.pop().popAndRemove().push("child2").getCurrentNode();
    StopWatchTreeNode child1b = path.pop().push("child1").getCurrentNode();
    path.pop();

    assertEquals(root, path.getCurrentNode());
    List<StopWatchTreeNode> observedChildren = Lists.asList(root.getChildren());
    assertEquals(2, observedChildren.size());
    assertTrue(observedChildren.contains(child1b));
    assertTrue(observedChildren.contains(child2));

    assertEquals(1, child1.getNumChildren());
    assertEquals(grandchild, Lists.asList(child1.getChildren()).get(0));
    assertEquals(0, grandchild.getNumChildren());
    assertEquals(0, child2.getNumChildren());
    assertEquals(0, child1b.getNumChildren());

    TimingTree expected = new TimingTree(
        new TimingTreeNode(0, "Total Elapsed Time", 400L,
            new TimingTreeNode(1, "child1", 300L),
            new TimingTreeNode(1, "child1", 200L,
                new TimingTreeNode(2, "grandchild", 100L)),
            new TimingTreeNode(1, "child2", 150L)));

    assertEquals(expected, path.getCurrentTiming());
    verify(watches.toArray());
  }

  public void testPopAndRemove_branchNode() {
    List<StopWatch> watches = getMockWatches();
    replay(watches.toArray());

    StopWatchTreePath path = new StopWatchTreePath(new MyStopWatchTreeProvider(watches));
    StopWatchTreeNode root = path.getCurrentNode();
    StopWatchTreeNode child1 = path.push("child1").getCurrentNode();
    StopWatchTreeNode grandchild = path.push("grandchild").getCurrentNode();
    StopWatchTreeNode child2 = path.pop().popAndRemove().push("child2").getCurrentNode();
    path.pop();

    assertEquals(root, path.getCurrentNode());
    assertEquals(1, root.getNumChildren());
    assertEquals(child2, Lists.asList(root.getChildren()).get(0));
    assertEquals(1, child1.getNumChildren());
    assertEquals(grandchild, Lists.asList(child1.getChildren()).get(0));
    assertEquals(0, grandchild.getNumChildren());
    assertEquals(0, child2.getNumChildren());

    assertEquals(getTimingTreeForMockWatches(), path.getCurrentTiming());
    verify(watches.toArray());
  }

  public void testPopAndRemove_notRunning() {
    StopWatchTreePath path = getTreePathForPopNotRunningTest();

    try {
      path.popAndRemove();
      fail("Cannot push an element onto a stopped tree.");
    } catch (PreconditionException e) {
      // cannot push an element onto a stopped tree
    }
  }

  public void testPopAndRemove_atRoot() {
    StopWatchTreePath path = getTreePathForPopAtRootTest();

    try {
      path.popAndRemove();
      fail("Cannot pop the root element from a tree.");
    } catch (PreconditionException e) {
      // Cannot pop the root element from a tree;
    }
  }
}
