// Copyright 2008 Google Inc.
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

package com.google.android.stardroid.util;

import com.google.android.stardroid.base.Provider;

/**
 * A simple watch utility for calculating the wall clock time for processes to complete.
 *
 * @author Brent Bryan
 */

public class StopWatchImpl implements StopWatch {
  private static final Provider<StopWatch> WATCH_PROVIDER = new Provider<StopWatch>() {
    @Override
    public StopWatch get() {
      return new StopWatchImpl();
    }
  };

  private static final long NOT_RUNNING = -1L;

  private long startTime = NOT_RUNNING;
  private long elapsedTime = 0L;

  @Override
  public StopWatchImpl start() {
    if (startTime != NOT_RUNNING) {
      throw new RuntimeException("Watch already running!");
    }
    startTime = System.currentTimeMillis();
    return this;
  }

  @Override
  public StopWatchImpl stop() {
    if (startTime != NOT_RUNNING) {
      elapsedTime += System.currentTimeMillis() - startTime;
      startTime = NOT_RUNNING;
    }
    return this;
  }

  @Override
  public boolean isRunning() {
    return startTime != NOT_RUNNING;
  }

  @Override
  public StopWatchImpl clear() {
    startTime = NOT_RUNNING;
    elapsedTime = 0L;
    return this;
  }

  /**
   * Returns the total elapsed time the watch has been running since creation, or since the last
   * clear() call was made.
   */
  @Override
  public long getElapsedTime() {
    return elapsedTime + getRunningTime();
  }

  /**
   * Return the time since start was called. If the StopWatch is not running, returns 0.
   */
  @Override
  public long getRunningTime() {
    if (startTime == NOT_RUNNING) {
      return 0;
    }
    return System.currentTimeMillis() - startTime;
  }

  /** Returns a human readable String describing the specified amount of time. */
  public static String formatTime(long time) {
    long minutes = time / 60000L;
    time -= 60000L * minutes;
    long seconds = time / 1000L;
    time -= 1000L * seconds;
    return String.format("%02dm %02ds %03dms", minutes, seconds, time);
  }

  @Override
  public String formatTime() {
    return formatTime(getElapsedTime());
  }

  /**
   * Calls stop() and then clear()'s the watch, returning a human readable version of the elapsed
   * time (before it was cleared).
   */
  @Override
  public String end() {
    stop();
    String result = formatTime();
    clear();
    return result;
  }

  @Override
  public String endStart() {
    String result = end();
    start();
    return result;
  }

  public static Provider<StopWatch> getProvider() {
    return WATCH_PROVIDER;
  }
}
