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

import com.google.android.stardroid.base.Preconditions;
import com.google.android.stardroid.base.Provider;
import com.google.android.stardroid.base.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

/**
 * A path through a {@link StopWatchTree} used to easily perform nested timings.
 * Each time {@link #push} is called this {@link StopWatchTreePath} descends to
 * the appropriate child of underlying {@link StopWatchTree}, creating a new
 * node if necessary. Conversely, {@link #pop} stops the timer at the current
 * level of the {@link StopWatchTree} and updates this {@link StopWatchTreePath}
 * to point at the node's parent.
 * <p>
 * NOTE: clients should not call methods on the {@link StopWatchTree} directly.
 * <p>
 * The general usage pattern is:
 *
 * <code><pre>
 * StopWatchTreePath path = new StopWatchTreePath(new StopWatchTree()).start();
 *
 * path.push("foo");
 *   <i>do some foo work<i>
 * path.pop();
 *
 * path.push("bar").push("soap");
 *   <i> do some soap bar work</i>
 * path.popAndRemove().popAndRemove();
 *
 * path.push("foo");
 *   <i>do more foo work<i>
 * path.popAndRemove();
 *
 * TimingTree result = path.getTimings();
 *
 * <i>even more work<i>
 *
 * path.stop();
 * path.getTimings();
 * </pre></code>
 *
 * @author Brent Bryan
 */
public class StopWatchTreePath {
  private final StopWatchTree tree;
  private final Stack<StopWatchTreeNode> stack = new Stack<StopWatchTreeNode>();
  private final HashMap<StopWatchTreeNode, List<StopWatchTreeNode>> removedNodes =
      new HashMap<StopWatchTreeNode, List<StopWatchTreeNode>>();

  /**
   * Constructs a new {@link StopWatchTreePath} using a default implementation
   * of a {@link StopWatchTree}. Sets the root node as the
   * {@link StopWatchTreePath}'s current node.
   */
  public StopWatchTreePath() {
    this(StopWatchTree.getProvider());
  }

  /**
   * Constructs a new {@link StopWatchTreePath} using the given
   * {@link StopWatchTree} as the underlying hierarchical timer representation.
   * Sets the root node as the {@link StopWatchTreePath}'s current node.
   */
  public StopWatchTreePath(Provider<StopWatchTree> treeProvider) {
    this.tree = treeProvider.get().start();
    stack.push(tree.getRoot());
  }

  /**
   * Resets and restarts the underlying {@link StopWatchTree}. Sets the current
   * node to be the root of the tree. All children and timings will be lost.
   */
  public StopWatchTreePath reset() {
    removedNodes.clear();
    tree.reset().start();
    stack.removeAllElements();
    stack.push(tree.getRoot());
    return this;
  }

  /**
   * Descends the underlying {@link StopWatchTree} to a child of the current
   * node with the given name, creating a new child if necessary. Starts the
   * timer on the child node, and sets the child as the current node of this
   * {@link StopWatchTreePath}.
   *
   * @throws RuntimeException if the underlying {@link StopWatchTree} is not
   *         running
   * @return A reference to this object for chaining
   */
  public StopWatchTreePath push(String name) {
    Preconditions.check(tree.isRunning());
    stack.push(stack.peek().getChild(name)).getStopWatch().start();
    return this;
  }

  /**
   * Stops the timer in the current node and sets the parent of the current node
   * as the current node in this {@link StopWatchTreePath}. This operation does
   * not cause the value of the current node to be recorded or erased. As such,
   * users can {@link #push} down into the current node again at a later time
   * and continue timing from where they left off.
   *
   * @throws RuntimeException if the underlying {@link StopWatchTree} is not
   *         running, or if the current node is the root node
   * @return A reference to this object for chaining
   */
  public StopWatchTreePath pop() {
    Preconditions.check(tree.isRunning() && stack.size() > 1);
    stack.pop().getStopWatch().stop();
    return this;
  }

  /**
   * Returns the current {@link StopWatchTreeNode}.
   */
  public StopWatchTreeNode getCurrentNode() {
    return stack.peek();
  }

  /**
   * Records the value of the timer in the current node, removes the current
   * node from the underlying {@link StopWatchTree}, and sets the parent of the
   * current node as the current node in this {@link StopWatchTreePath}.
   *
   *
   * @throws RuntimeException if the underlying {@link StopWatchTree} is not
   *         running, or if the current node is the root node
   * @return A reference to this object for chaining
   */
  public StopWatchTreePath popAndRemove() {
    Preconditions.check(tree.isRunning() && stack.size() > 1);
    StopWatchTreeNode node = stack.pop().stop();
    saveRemovedNode(stack.peek(), node);
    stack.peek().removeChild(node.getName());
    return this;
  }

  /**
   * Returns the {@link TimingTree} describing the current state of the
   * underlying {@link StopWatchTree}. See
   * {@link StopWatchTree#getCurrentTiming()}
   */
  // We have to reimplement this method so that we can include all the removed
  // nodes, as well as those currently in the tree.
  public TimingTree getCurrentTiming() {
    return new TimingTree(createTimingForNode(0, tree.getRoot()));
  }

  private TimingTreeNode createTimingForNode(int level, StopWatchTreeNode node) {
    long elapsedTime = node.getStopWatch().getElapsedTime();

    List<TimingTreeNode> children = new ArrayList<TimingTreeNode>();
    for (StopWatchTreeNode child : node.getChildren()) {
      children.add(createTimingForNode(level + 1, child));
    }
    if (removedNodes.containsKey(node)) {
      for (StopWatchTreeNode child : removedNodes.get(node)) {
        children.add(createTimingForNode(level + 1, child));
      }
    }
    return new TimingTreeNode(level, node.getName(), elapsedTime, children);
  }

  @VisibleForTesting
  void saveRemovedNode(StopWatchTreeNode parent, StopWatchTreeNode node) {
    List<StopWatchTreeNode> nodes = removedNodes.get(parent);
    if (nodes == null) {
      nodes = new ArrayList<StopWatchTreeNode>();
      removedNodes.put(parent, nodes);
    }
    nodes.add(node);
  }
}
