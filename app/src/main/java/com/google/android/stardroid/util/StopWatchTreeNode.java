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

import com.google.android.stardroid.base.Provider;

import java.util.HashMap;
import java.util.Iterator;

/**
 * A class representing a node in the StopWatchTree.
 *
 * @author Brent Bryan
 */
public class StopWatchTreeNode {
  private final String name;
  private final Provider<StopWatch> watchProvider;
  private final StopWatch watch;
  private final HashMap<String, StopWatchTreeNode> children =
    new HashMap<String, StopWatchTreeNode>();

  public StopWatchTreeNode(Provider<StopWatch> watchProvider, String name) {
    this.watchProvider = watchProvider;
    this.watch = watchProvider.get();
    this.name = name;
  }

  /** Returns the name associated with this node. */
  public String getName() {
    return name;
  }

  /** Returns the {@link StopWatch} associated with this node. */
  public StopWatch getStopWatch() {
    return watch;
  }

  /** Returns the number of children of this node. */
  public int getNumChildren() {
    return children.size();
  }

  /** Returns an {@link Iterable} over the children of this node. */
  public Iterable<StopWatchTreeNode> getChildren() {
    return new Iterable<StopWatchTreeNode>() {
      @Override
      public Iterator<StopWatchTreeNode> iterator() {
        return children.values().iterator();
      }
    };
  }

  /**
   * Returns the child node with the given name from this branch node, creating
   * a new child node if necessary.
   *
   * @return A reference to this object for chaining
   */
  public StopWatchTreeNode getChild(String childName) {
    StopWatchTreeNode child = children.get(childName);
    if (child == null) {
      child = new StopWatchTreeNode(watchProvider, childName);
      children.put(childName, child);
    }
    return child;
  }

  /**
   * Removes and returns the child node with the given name from this branch
   * node. Returns null if no child with the given name exists under this node.
   *
   * @return A reference to this object for chaining
   */
  public StopWatchTreeNode removeChild(String childName) {
    return children.remove(childName);
  }

  /**
   * Starts the {@link StopWatch} contained in this node. Does not affect the
   * {@link StopWatch}s contained in child nodes, if present.
   *
   * @return A reference to this object for chaining
   */
  public StopWatchTreeNode start() {
    watch.start();
    return this;
  }

  /**
   * Stops the {@link StopWatch} contained in this node, as well as all
   * {@link StopWatch}s in child nodes, if present.
   *
   * @return A reference to this object for chaining
   */
  public StopWatchTreeNode stop() {
    for (StopWatchTreeNode child : children.values()) {
      child.stop();
    }
    watch.stop();
    return this;
  }

  /**
   * Removes all children from this {@link StopWatchTreeNode} and resets this
   * node's {@link StopWatch}.  No information is retained.
   *
   * @return A reference to this object for chaining
   */
  public StopWatchTreeNode reset() {
    for (StopWatchTreeNode child : children.values()) {
      child.reset();
    }
    children.clear();
    watch.clear();
    return this;
  }
}
