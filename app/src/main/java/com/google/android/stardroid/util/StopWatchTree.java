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

import java.util.ArrayList;
import java.util.List;

/**
 * A tree of {@link StopWatchImpl}s used to perform nested timings of methods. While
 * it is possible to use this class directly to compute nested timings, users
 * may find wrapping this class with a {@link StopWatchTreePath} simpler.
 *
 * @author Brent Bryan
 */
public class StopWatchTree {
  private static final Provider<StopWatchTree> TREE_PROVIDER = new Provider<StopWatchTree>() {
    @Override
    public StopWatchTree get() {
      return new StopWatchTree(StopWatchImpl.getProvider());
    }
  };


  private final StopWatchTreeNode root;

  public StopWatchTree(Provider<StopWatch> watchProvider) {
    root = new StopWatchTreeNode(watchProvider, "Total Elapsed Time");
  }

  /**
   * Returns the root node of this {@link StopWatchTree}. The result will never
   * be null.
   */
  public StopWatchTreeNode getRoot() {
    return root;
  }

  /**
   * Starts the root timer for this {@link StopWatchTree}. Timers in children
   * nodes will remain unstarted.
   *
   * @throws RuntimeException if this method is called when {@link #isRunning}
   *         is true.
   * @return A reference to the root StopWatchTreeNode object for chaining
   */
  public StopWatchTree start() {
    Preconditions.check(!isRunning());
    root.start();
    return this;
  }

  /**
   * Stops all currently running watches in the {@link StopWatchTree}, retaining
   * all information.
   *
   * @throws RuntimeException if this method is called when {@link #isRunning}
   *         is false.
   * @return A reference to this object for chaining
   */
  public StopWatchTree stop() {
    Preconditions.check(isRunning());
    root.stop();
    return this;
  }

  /**
   * Returns true if this {@link StopWatchTree} is running. That is, returns
   * true if {@link #start} or {@link #reset} have been called subsequent to the
   * last invocation of {@link #stop} (or creation of the
   * {@link StopWatchTree}).
   */
  public boolean isRunning() {
    return root.getStopWatch().isRunning();
  }

  /**
   * Resets the {@link StopWatchTree}, removing all children, clearing the state
   * of all watches (running or not) and setting the state of this StopWatchTree
   * to non running.
   *
   * @return A reference to the root StopWatchTreeNode object for chaining
   */
  public StopWatchTree reset() {
    root.reset();
    return this;
  }

  /**
   * Returns a {@link TimingTree} describing the timing values at watch
   * node (including those nodes which have been {@link #stop}ed). This method
   * has no side effects on this {@link StopWatchTree}.
   *
   * @return StopWatchTreeStatus representing the current durations for each
   *         node in this {@link StopWatchTree}.
   */
  public TimingTree getCurrentTiming() {
    return new TimingTree(createTimingForNode(0, root));
  }

  private TimingTreeNode createTimingForNode(int level, StopWatchTreeNode node) {
    long elapsedTime = node.getStopWatch().getElapsedTime();
    List<TimingTreeNode> children = new ArrayList<TimingTreeNode>(node.getNumChildren());
    for (StopWatchTreeNode child : node.getChildren()) {
      children.add(createTimingForNode(level + 1, child));
    }
    return new TimingTreeNode(level, node.getName(), elapsedTime, children);
  }

  public static Provider<StopWatchTree> getProvider() {
    return TREE_PROVIDER;
  }

}
